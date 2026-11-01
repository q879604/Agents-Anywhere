package com.agentsanywhere.app.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.agentsanywhere.app.R
import com.agentsanywhere.app.api.SessionSearchHit
import com.agentsanywhere.app.feature.sessions.SessionsState
import com.agentsanywhere.app.model.AgentDevice
import com.agentsanywhere.app.model.AgentProject
import com.agentsanywhere.app.model.AgentSession
import com.agentsanywhere.app.ui.designsystem.LocalAAColors
import com.agentsanywhere.app.ui.designsystem.noRippleClickable
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Clock3
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Monitor
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.X

private data class WorkspaceOption(val path: String, val label: String)

private data class HomeSearchResults(
    val sessions: List<AgentSession>,
    val projects: List<AgentProject>,
    val devices: List<AgentDevice>,
) {
    val isEmpty: Boolean get() = sessions.isEmpty() && projects.isEmpty() && devices.isEmpty()
}

@Composable
internal fun HomeSearchOverlay(
    state: SessionsState,
    projectSessionsById: Map<String, List<AgentSession>>,
    recentSearches: List<String>,
    onDismiss: () -> Unit,
    onQueryCommitted: (String) -> Unit,
    onClearHistory: () -> Unit,
    onOpenSession: (AgentSession) -> Unit,
    onOpenSessionById: (String) -> Unit,
    onOpenProject: (AgentProject) -> Unit,
    onOpenDevice: (AgentDevice) -> Unit,
    onSearchSessions: suspend (String, String?, String?) -> Result<List<SessionSearchHit>>,
) {
    val colors = LocalAAColors.current
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var query by remember { mutableStateOf("") }
    var submittedQuery by remember { mutableStateOf("") }
    var fullText by remember { mutableStateOf(false) }
    var selectedDeviceId by remember { mutableStateOf<String?>(null) }
    var selectedWorkspace by remember { mutableStateOf<String?>(null) }
    var fullTextResults by remember { mutableStateOf<List<SessionSearchHit>>(emptyList()) }
    var fullTextLoading by remember { mutableStateOf(false) }
    var fullTextError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    fun commitSearch(value: String = query) {
        val trimmed = value.trim()
        if (trimmed.isNotEmpty()) onQueryCommitted(trimmed)
        focusManager.clearFocus()
    }

    fun openSession(session: AgentSession) {
        commitSearch()
        onOpenSession(session)
    }

    val loadedSessions = remember(projectSessionsById, state.sessions, state.archivedSessions) {
        (projectSessionsById.values.flatten() + state.sessions + state.archivedSessions)
            .associateBy(AgentSession::id)
            .values
            .toList()
    }
    val workspaceOptions = remember(state.projects, loadedSessions) {
        val fromProjects = state.projects.mapNotNull { project ->
            project.workspacePath.takeIf(String::isNotBlank)?.let { WorkspaceOption(it, project.name) }
        }
        val fromSessions = loadedSessions.mapNotNull { session ->
            session.cwd?.takeIf(String::isNotBlank)?.let { path ->
                WorkspaceOption(
                    path = path,
                    label = session.workspaceLabel.ifBlank { path.substringAfterLast('/').ifBlank { path } },
                )
            }
        }
        (fromProjects + fromSessions).distinctBy { it.path }.take(40)
    }
    val results = remember(
        submittedQuery,
        state.sessions,
        state.archivedSessions,
        state.projects,
        state.devices,
        projectSessionsById,
        selectedDeviceId,
        selectedWorkspace,
    ) {
        buildHomeSearchResults(submittedQuery, state, projectSessionsById, selectedDeviceId, selectedWorkspace)
    }
    LaunchedEffect(submittedQuery, fullText, selectedDeviceId, selectedWorkspace) {
        if (!fullText || submittedQuery.isBlank()) {
            fullTextResults = emptyList()
            fullTextLoading = false
            fullTextError = false
            return@LaunchedEffect
        }
        fullTextLoading = true
        fullTextError = false
        delay(320)
        val result = onSearchSessions(submittedQuery, selectedDeviceId, selectedWorkspace)
        result.onSuccess {
            fullTextResults = it
            fullTextError = false
        }.onFailure {
            fullTextResults = emptyList()
            fullTextError = true
        }
        fullTextLoading = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            dismissOnBackPress = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        BackHandler(onBack = onDismiss)
        Surface(modifier = Modifier.fillMaxSize(), color = colors.canvas) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SearchIconButton(
                        icon = Lucide.ChevronLeft,
                        contentDescription = stringResource(R.string.common_back),
                        onClick = onDismiss,
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(colors.raisedSurface)
                            .border(1.dp, colors.border, RoundedCornerShape(15.dp))
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Lucide.Search,
                            contentDescription = null,
                            tint = colors.muted,
                            modifier = Modifier.size(18.dp),
                        )
                        BasicTextField(
                            value = query,
                            onValueChange = {
                                query = it
                                submittedQuery = it
                            },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = colors.ink,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            cursorBrush = SolidColor(colors.primaryAction),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { commitSearch() }),
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (query.isBlank()) {
                                        Text(
                                            text = stringResource(R.string.home_search_hint),
                                            color = colors.muted,
                                            fontSize = 14.sp,
                                        )
                                    }
                                    inner()
                                }
                            },
                        )
                        if (query.isNotBlank()) {
                            Icon(
                                imageVector = Lucide.X,
                                contentDescription = stringResource(R.string.home_search_clear),
                                tint = colors.muted,
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .noRippleClickable {
                                        query = ""
                                        submittedQuery = ""
                                        focusRequester.requestFocus()
                                    }
                                    .padding(4.dp),
                            )
                        }
                    }
                }
                SearchFilterBar(
                    devices = state.devices,
                    workspaces = workspaceOptions,
                    selectedDeviceId = selectedDeviceId,
                    selectedWorkspace = selectedWorkspace,
                    fullText = fullText,
                    onDeviceSelected = { selectedDeviceId = it },
                    onWorkspaceSelected = { selectedWorkspace = it },
                    onFullTextChanged = { fullText = it },
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
                if (submittedQuery.isBlank()) {
                    RecentSearchContent(
                        recentSearches = recentSearches,
                        onSelect = {
                            query = it
                            submittedQuery = it
                        },
                        onClear = onClearHistory,
                    )
                } else if (fullText) {
                    FullTextSearchContent(
                        hits = fullTextResults,
                        query = submittedQuery,
                        loading = fullTextLoading,
                        failed = fullTextError,
                        onOpenSession = {
                            commitSearch()
                            onOpenSessionById(it.sessionId)
                        },
                    )
                } else if (results.isEmpty) {
                    NoSearchResults(query = submittedQuery)
                } else {
                    SearchResultsContent(
                        results = results,
                        query = submittedQuery,
                        state = state,
                        onOpenSession = ::openSession,
                        onOpenProject = {
                            commitSearch()
                            onOpenProject(it)
                        },
                        onOpenDevice = {
                            commitSearch()
                            onOpenDevice(it)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchFilterBar(
    devices: List<AgentDevice>,
    workspaces: List<WorkspaceOption>,
    selectedDeviceId: String?,
    selectedWorkspace: String?,
    fullText: Boolean,
    onDeviceSelected: (String?) -> Unit,
    onWorkspaceSelected: (String?) -> Unit,
    onFullTextChanged: (Boolean) -> Unit,
) {
    val colors = LocalAAColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterMenuButton(
            label = devices.firstOrNull { it.id == selectedDeviceId }?.name
                ?: stringResource(R.string.home_search_all_devices),
            selected = selectedDeviceId != null,
            items = listOf(null to stringResource(R.string.home_search_all_devices)) +
                devices.map { it.id to it.name },
            onSelect = onDeviceSelected,
        )
        FilterMenuButton(
            label = workspaces.firstOrNull { it.path == selectedWorkspace }?.label
                ?: stringResource(R.string.home_search_all_workspaces),
            selected = selectedWorkspace != null,
            items = listOf(null to stringResource(R.string.home_search_all_workspaces)) +
                workspaces.map { it.path to it.label },
            onSelect = onWorkspaceSelected,
        )
        Row(
            modifier = Modifier
                .height(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (fullText) colors.primaryAction.copy(alpha = 0.16f) else colors.raisedSurface)
                .border(1.dp, if (fullText) colors.primaryAction.copy(alpha = 0.5f) else colors.border, RoundedCornerShape(10.dp))
                .noRippleClickable { onFullTextChanged(!fullText) }
                .padding(horizontal = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Lucide.Search,
                contentDescription = null,
                tint = if (fullText) colors.primaryAction else colors.muted,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = stringResource(R.string.home_search_all_text),
                color = if (fullText) colors.primaryAction else colors.ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun FilterMenuButton(
    label: String,
    selected: Boolean,
    items: List<Pair<String?, String>>,
    onSelect: (String?) -> Unit,
) {
    val colors = LocalAAColors.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .height(34.dp)
                .widthIn(max = 190.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (selected) colors.primaryAction.copy(alpha = 0.14f) else colors.raisedSurface)
                .border(1.dp, if (selected) colors.primaryAction.copy(alpha = 0.42f) else colors.border, RoundedCornerShape(10.dp))
                .noRippleClickable { expanded = true }
                .padding(horizontal = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f, fill = false),
                color = if (selected) colors.primaryAction else colors.ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Lucide.ChevronDown,
                contentDescription = null,
                tint = colors.muted,
                modifier = Modifier.size(14.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { (value, text) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = text,
                            color = if (value == null) colors.muted else colors.ink,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

@Composable
private fun FullTextSearchContent(
    hits: List<SessionSearchHit>,
    query: String,
    loading: Boolean,
    failed: Boolean,
    onOpenSession: (SessionSearchHit) -> Unit,
) {
    val colors = LocalAAColors.current
    if (loading) {
        Box(Modifier.fillMaxSize().padding(top = 80.dp), contentAlignment = Alignment.TopCenter) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = colors.primaryAction)
        }
        return
    }
    if (failed) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.home_search_full_text_failed),
                color = colors.ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        return
    }
    if (hits.isEmpty()) {
        NoSearchResults(query = query)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item(key = "header:full") {
            SearchSectionHeader(stringResource(R.string.home_search_full_text_results), hits.size)
        }
        items(hits, key = { "full:${it.sessionId}" }) { hit ->
            SessionSearchHitRow(hit = hit, query = query, onClick = { onOpenSession(hit) })
        }
        item(key = "bottom-space") { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
private fun SessionSearchHitRow(hit: SessionSearchHit, query: String, onClick: () -> Unit) {
    val colors = LocalAAColors.current
    val context = listOfNotNull(hit.projectName, hit.deviceName, hit.cwd)
        .filter(String::isNotBlank)
        .joinToString(" · ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(14.dp))
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(colors.subtle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Lucide.Search, null, tint = colors.muted, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            HighlightedSearchText(
                text = hit.title.ifBlank { hit.cwd.orEmpty() },
                query = query,
                color = colors.ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            HighlightedSearchText(
                text = hit.snippet,
                query = query,
                color = colors.muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 2,
            )
            if (context.isNotBlank()) {
                Text(
                    text = context,
                    color = colors.muted.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(Lucide.ChevronRight, null, tint = colors.muted.copy(alpha = 0.7f), modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun SearchIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val colors = LocalAAColors.current
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .noRippleClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = colors.ink, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun RecentSearchContent(
    recentSearches: List<String>,
    onSelect: (String) -> Unit,
    onClear: () -> Unit,
) {
    val colors = LocalAAColors.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (recentSearches.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 70.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Lucide.Search, null, tint = colors.muted, modifier = Modifier.size(32.dp))
                    Text(
                        text = stringResource(R.string.home_search_start_title),
                        color = colors.ink,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.home_search_start_body),
                        color = colors.muted,
                        fontSize = 13.sp,
                    )
                }
            }
        } else {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.home_search_recent),
                        modifier = Modifier.weight(1f),
                        color = colors.muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.home_search_clear_history),
                        modifier = Modifier.noRippleClickable(onClick = onClear).padding(6.dp),
                        color = colors.muted,
                        fontSize = 12.sp,
                    )
                }
            }
            items(recentSearches, key = { "recent:$it" }) { value ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .noRippleClickable { onSelect(value) }
                        .padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Lucide.Clock3, null, tint = colors.muted, modifier = Modifier.size(18.dp))
                    Text(
                        text = value,
                        modifier = Modifier.weight(1f),
                        color = colors.ink,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(Lucide.ChevronRight, null, tint = colors.muted, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun SearchResultsContent(
    results: HomeSearchResults,
    query: String,
    state: SessionsState,
    onOpenSession: (AgentSession) -> Unit,
    onOpenProject: (AgentProject) -> Unit,
    onOpenDevice: (AgentDevice) -> Unit,
) {
    val colors = LocalAAColors.current
    val projectNames = remember(state.projects) { state.projects.associate { it.id to it.name } }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (results.sessions.isNotEmpty()) {
            item(key = "header:sessions") { SearchSectionHeader(stringResource(R.string.home_search_sessions), results.sessions.size) }
            items(results.sessions, key = { "session:${it.id}" }) { session ->
                SessionSearchRow(
                    session = session,
                    query = query,
                    projectName = session.projectId?.let(projectNames::get),
                    onClick = { onOpenSession(session) },
                )
            }
        }
        if (results.projects.isNotEmpty()) {
            item(key = "header:projects") { SearchSectionHeader(stringResource(R.string.home_search_projects), results.projects.size) }
            items(results.projects, key = { "project:${it.id}" }) { project ->
                ProjectSearchRow(project = project, query = query, onClick = { onOpenProject(project) })
            }
        }
        if (results.devices.isNotEmpty()) {
            item(key = "header:devices") { SearchSectionHeader(stringResource(R.string.home_search_devices), results.devices.size) }
            items(results.devices, key = { "device:${it.id}" }) { device ->
                DeviceSearchRow(device = device, query = query, onClick = { onOpenDevice(device) })
            }
        }
        item(key = "bottom-space") { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
private fun SearchSectionHeader(title: String, count: Int) {
    val colors = LocalAAColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = colors.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Text(count.toString(), color = colors.muted.copy(alpha = 0.6f), fontSize = 11.sp)
    }
}

@Composable
private fun SessionSearchRow(
    session: AgentSession,
    query: String,
    projectName: String?,
    onClick: () -> Unit,
) {
    val colors = LocalAAColors.current
    val context = listOfNotNull(projectName, session.deviceName, session.workspaceLabel)
        .filter(String::isNotBlank)
        .joinToString(" · ")
    SearchResultRow(
        icon = Lucide.Search,
        title = session.title.ifBlank { session.workspaceLabel },
        subtitle = context,
        query = query,
        onClick = onClick,
    )
}

@Composable
private fun ProjectSearchRow(project: AgentProject, query: String, onClick: () -> Unit) {
    SearchResultRow(
        icon = Lucide.Folder,
        title = project.name,
        subtitle = project.workspacePath,
        query = query,
        onClick = onClick,
    )
}

@Composable
private fun DeviceSearchRow(device: AgentDevice, query: String, onClick: () -> Unit) {
    val colors = LocalAAColors.current
    SearchResultRow(
        icon = Lucide.Monitor,
        title = device.name,
        subtitle = listOfNotNull(device.deviceOs, if (device.online) stringResource(R.string.home_search_online) else stringResource(R.string.home_search_offline)).joinToString(" · "),
        query = query,
        onClick = onClick,
    )
}

@Composable
private fun SearchResultRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    query: String,
    onClick: () -> Unit,
) {
    val colors = LocalAAColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(14.dp))
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(colors.subtle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = colors.muted, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            HighlightedSearchText(
                text = title,
                query = query,
                color = colors.ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = colors.muted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(Lucide.ChevronRight, null, tint = colors.muted.copy(alpha = 0.7f), modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun HighlightedSearchText(
    text: String,
    query: String,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight,
    maxLines: Int = 1,
) {
    val colors = LocalAAColors.current
    val start = if (query.isBlank()) -1 else text.indexOf(query, ignoreCase = true)
    val annotated: AnnotatedString = if (start < 0) {
        AnnotatedString(text)
    } else {
        buildAnnotatedString {
            append(text)
            addStyle(
                SpanStyle(color = colors.primaryAction, fontWeight = FontWeight.Bold),
                start,
                (start + query.length).coerceAtMost(text.length),
            )
        }
    }
    Text(
        text = annotated,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun NoSearchResults(query: String) {
    val colors = LocalAAColors.current
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Lucide.Search, null, tint = colors.muted, modifier = Modifier.size(32.dp))
        Text(
            text = stringResource(R.string.home_search_no_results_title),
            color = colors.ink,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.home_search_no_results_body, query),
            color = colors.muted,
            fontSize = 13.sp,
        )
    }
}

private fun buildHomeSearchResults(
    query: String,
    state: SessionsState,
    projectSessionsById: Map<String, List<AgentSession>>,
    selectedDeviceId: String? = null,
    selectedWorkspace: String? = null,
): HomeSearchResults {
    val needle = query.trim()
    if (needle.isBlank()) return HomeSearchResults(emptyList(), emptyList(), emptyList())
    val projectNames = state.projects.associate { it.id to it.name }
    val sessions = (state.sessions + state.archivedSessions + projectSessionsById.values.flatten())
        .distinctBy { it.id }
        .filter { session ->
            (selectedDeviceId == null || session.connectorId == selectedDeviceId) &&
                (selectedWorkspace == null || session.cwd == selectedWorkspace ||
                    session.cwd.orEmpty().startsWith(selectedWorkspace.trimEnd('/') + "/"))
        }
        .mapNotNull { session ->
            val title = session.title
            val projectName = session.projectId?.let(projectNames::get).orEmpty()
            val haystack = listOf(
                title,
                session.summary,
                session.cwd.orEmpty(),
                session.workspaceLabel,
                session.deviceName,
                projectName,
            ).joinToString("\n")
            if (!haystack.contains(needle, ignoreCase = true)) return@mapNotNull null
            val score = when {
                title.equals(needle, ignoreCase = true) -> 0
                title.startsWith(needle, ignoreCase = true) -> 1
                title.contains(needle, ignoreCase = true) -> 2
                projectName.contains(needle, ignoreCase = true) -> 3
                else -> 4
            }
            score to session
        }
        .sortedWith(compareBy<Pair<Int, AgentSession>> { it.first }.thenByDescending { it.second.updatedSeq })
        .map { it.second }
        .take(24)
    val projects = state.projects
        .filter { selectedWorkspace == null || it.workspacePath == selectedWorkspace }
        .filter { it.name.contains(needle, ignoreCase = true) || it.workspacePath.contains(needle, ignoreCase = true) }
        .sortedWith(compareBy<AgentProject> { if (it.name.startsWith(needle, ignoreCase = true)) 0 else 1 }.thenByDescending { it.pinned })
        .take(8)
    val devices = state.devices
        .filter { selectedDeviceId == null || it.id == selectedDeviceId }
        .filter {
            it.name.contains(needle, ignoreCase = true) ||
                it.id.contains(needle, ignoreCase = true) ||
                it.deviceOs.orEmpty().contains(needle, ignoreCase = true)
        }
        .sortedByDescending { it.online }
        .take(8)
    return HomeSearchResults(sessions, projects, devices)
}
