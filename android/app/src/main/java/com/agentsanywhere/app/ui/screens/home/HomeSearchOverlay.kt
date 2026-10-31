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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.agentsanywhere.app.feature.sessions.SessionsState
import com.agentsanywhere.app.model.AgentDevice
import com.agentsanywhere.app.model.AgentProject
import com.agentsanywhere.app.model.AgentSession
import com.agentsanywhere.app.ui.designsystem.LocalAAColors
import com.agentsanywhere.app.ui.designsystem.noRippleClickable
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Clock3
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Monitor
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.X

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
    onOpenProject: (AgentProject) -> Unit,
    onOpenDevice: (AgentDevice) -> Unit,
) {
    val colors = LocalAAColors.current
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var query by remember { mutableStateOf("") }
    var submittedQuery by remember { mutableStateOf("") }

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

    val results = remember(submittedQuery, state.sessions, state.archivedSessions, state.projects, state.devices, projectSessionsById) {
        buildHomeSearchResults(submittedQuery, state, projectSessionsById)
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
        maxLines = 1,
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
): HomeSearchResults {
    val needle = query.trim()
    if (needle.isBlank()) return HomeSearchResults(emptyList(), emptyList(), emptyList())
    val projectNames = state.projects.associate { it.id to it.name }
    val sessions = (state.sessions + state.archivedSessions + projectSessionsById.values.flatten())
        .distinctBy { it.id }
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
        .filter { it.name.contains(needle, ignoreCase = true) || it.workspacePath.contains(needle, ignoreCase = true) }
        .sortedWith(compareBy<AgentProject> { if (it.name.startsWith(needle, ignoreCase = true)) 0 else 1 }.thenByDescending { it.pinned })
        .take(8)
    val devices = state.devices
        .filter {
            it.name.contains(needle, ignoreCase = true) ||
                it.id.contains(needle, ignoreCase = true) ||
                it.deviceOs.orEmpty().contains(needle, ignoreCase = true)
        }
        .sortedByDescending { it.online }
        .take(8)
    return HomeSearchResults(sessions, projects, devices)
}
