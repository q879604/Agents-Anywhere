from __future__ import annotations

import json
from typing import Any, Iterable

from fastapi import APIRouter, Depends, Query
from sqlalchemy import exists, or_, select

from agent_server.core.utc import utc_now
from agent_server.deps import current_user_id, get_store
from agent_server.infra.db import (
    connectors as connectors_t,
    projects as projects_t,
    sessions as sessions_t,
    timeline_items as timeline_items_t,
)
from agent_server.infra.repositories.facade import Store

router = APIRouter(prefix="/search", tags=["search"])

_MAX_QUERY_CHARS = 200
_MAX_RESULTS = 50
_MAX_TIMELINE_ROWS = 2_000


def _like_pattern(query: str) -> str:
    escaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    return f"%{escaped}%"


def _iter_text(value: Any, *, depth: int = 0) -> Iterable[str]:
    """Yield human-readable strings from a timeline payload.

    Timeline payloads also contain base64 image data.  Never scan those blobs:
    they are large and an accidental substring match is not useful to a user.
    """
    if depth > 8:
        return
    if isinstance(value, str):
        if 0 < len(value) <= 100_000:
            yield value
        return
    if isinstance(value, dict):
        for key, child in value.items():
            if str(key).lower() in {"base64", "data", "bytes", "image", "imagebytes"}:
                continue
            yield from _iter_text(child, depth=depth + 1)
        return
    if isinstance(value, list):
        for child in value:
            yield from _iter_text(child, depth=depth + 1)


def _snippet(payload_json: str, query: str, fallback: str) -> str:
    try:
        payload = json.loads(payload_json)
    except (TypeError, ValueError, json.JSONDecodeError):
        return fallback
    needle = query.casefold()
    for raw in _iter_text(payload):
        text = " ".join(raw.split())
        index = text.casefold().find(needle)
        if index < 0:
            continue
        start = max(0, index - 60)
        end = min(len(text), index + len(query) + 120)
        prefix = "…" if start else ""
        suffix = "…" if end < len(text) else ""
        return f"{prefix}{text[start:end]}{suffix}"[:280]
    return fallback


@router.get("")
async def search_sessions(
    q: str = Query(min_length=1, max_length=_MAX_QUERY_CHARS),
    deviceId: str | None = Query(default=None),
    workspace: str | None = Query(default=None),
    limit: int = Query(default=30, ge=1, le=_MAX_RESULTS),
    user_id: str = Depends(current_user_id),
    db: Store = Depends(get_store),
) -> dict[str, Any]:
    query = q.strip()
    if not query:
        return {"query": q, "results": [], "serverTime": utc_now()}

    pattern = _like_pattern(query)
    conditions = [
        connectors_t.c.user_id == user_id,
        connectors_t.c.revoked == 0,
        sessions_t.c.source_state != "deleted",
    ]
    if deviceId:
        conditions.append(sessions_t.c.connector_id == deviceId)
    if workspace and workspace.strip():
        root = workspace.strip().rstrip("/")
        conditions.append(
            or_(
                sessions_t.c.cwd == root,
                sessions_t.c.cwd.like(f"{root}/%"),
            )
        )

    title_match = or_(
        sessions_t.c.title.ilike(pattern, escape="\\"),
        sessions_t.c.cwd.ilike(pattern, escape="\\"),
        projects_t.c.name.ilike(pattern, escape="\\"),
    )
    timeline_match = exists(
        select(1).where(
            timeline_items_t.c.session_id == sessions_t.c.id,
            timeline_items_t.c.payload_json.ilike(pattern, escape="\\"),
        )
    )

    statement = (
        select(
            sessions_t.c.id,
            sessions_t.c.title,
            sessions_t.c.connector_id,
            sessions_t.c.project_id,
            sessions_t.c.cwd,
            sessions_t.c.archived,
            sessions_t.c.updated_seq,
            connectors_t.c.name.label("device_name"),
            projects_t.c.name.label("project_name"),
        )
        .select_from(
            sessions_t.join(connectors_t, connectors_t.c.id == sessions_t.c.connector_id)
            .outerjoin(projects_t, projects_t.c.id == sessions_t.c.project_id)
        )
        .where(*conditions, or_(title_match, timeline_match))
        .order_by(sessions_t.c.updated_seq.desc())
        .limit(limit)
    )

    async with db._engine.connect() as conn:
        rows = (await conn.execute(statement)).mappings().all()
        ids = [str(row["id"]) for row in rows]
        snippets: dict[str, str] = {}
        if ids:
            timeline_statement = (
                select(timeline_items_t.c.session_id, timeline_items_t.c.payload_json)
                .where(
                    timeline_items_t.c.session_id.in_(ids),
                    timeline_items_t.c.payload_json.ilike(pattern, escape="\\"),
                )
                .order_by(timeline_items_t.c.updated_seq.desc())
                .limit(_MAX_TIMELINE_ROWS)
            )
            for item in (await conn.execute(timeline_statement)).mappings():
                session_id = str(item["session_id"])
                if session_id not in snippets:
                    fallback = str(next(r["title"] for r in rows if str(r["id"]) == session_id) or "")
                    snippets[session_id] = _snippet(
                        str(item["payload_json"] or ""), query, fallback
                    )

    results = []
    for row in rows:
        session_id = str(row["id"])
        title = str(row["title"] or "")
        results.append(
            {
                "sessionId": session_id,
                "title": title,
                "snippet": snippets.get(session_id, title),
                "deviceId": str(row["connector_id"]),
                "deviceName": str(row["device_name"] or ""),
                "projectId": str(row["project_id"]) if row["project_id"] else None,
                "projectName": str(row["project_name"] or "") if row["project_name"] else None,
                "cwd": str(row["cwd"] or "") or None,
                "archived": bool(row["archived"]),
                "updatedSeq": int(row["updated_seq"] or 0),
            }
        )

    return {"query": query, "results": results, "serverTime": utc_now()}
