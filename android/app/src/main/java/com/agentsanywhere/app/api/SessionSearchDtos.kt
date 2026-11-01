package com.agentsanywhere.app.api

import org.json.JSONObject

data class SessionSearchHit(
    val sessionId: String,
    val title: String,
    val snippet: String,
    val deviceId: String,
    val deviceName: String,
    val projectId: String?,
    val projectName: String?,
    val cwd: String?,
    val archived: Boolean,
    val updatedSeq: Int,
)

internal fun JSONObject.toSessionSearchHit(): SessionSearchHit {
    return SessionSearchHit(
        sessionId = optString("sessionId"),
        title = optString("title"),
        snippet = optString("snippet"),
        deviceId = optString("deviceId"),
        deviceName = optString("deviceName"),
        projectId = optNullableString("projectId"),
        projectName = optNullableString("projectName"),
        cwd = optNullableString("cwd"),
        archived = optBoolean("archived", false),
        updatedSeq = optInt("updatedSeq", 0),
    )
}
