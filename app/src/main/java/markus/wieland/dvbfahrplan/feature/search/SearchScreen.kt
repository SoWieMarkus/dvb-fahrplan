package markus.wieland.dvbfahrplan.feature.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import markus.wieland.dvbfahrplan.R
import markus.wieland.dvbfahrplan.core.designsystem.Chip
import markus.wieland.dvbfahrplan.core.designsystem.FahrplanTheme
import markus.wieland.dvbfahrplan.core.designsystem.Hairline
import markus.wieland.dvbfahrplan.core.designsystem.Icon
import markus.wieland.dvbfahrplan.core.designsystem.IconButton
import markus.wieland.dvbfahrplan.core.designsystem.LineBadge
import markus.wieland.dvbfahrplan.core.designsystem.LiveDot
import markus.wieland.dvbfahrplan.core.designsystem.MessageBlock
import markus.wieland.dvbfahrplan.core.designsystem.SectionLabel
import markus.wieland.dvbfahrplan.core.designsystem.TextButton
import markus.wieland.dvbfahrplan.core.designsystem.Txt
import markus.wieland.dvbfahrplan.core.model.Departure
import markus.wieland.dvbfahrplan.core.model.Disruption
import markus.wieland.dvbfahrplan.core.model.ErrorKind
import markus.wieland.dvbfahrplan.core.model.Load
import markus.wieland.dvbfahrplan.core.model.Route
import markus.wieland.dvbfahrplan.core.model.SavedRoute
import markus.wieland.dvbfahrplan.core.model.Stop
import markus.wieland.dvbfahrplan.data.StopRepository

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onOpenRoute: (route: Route, origin: Stop, destination: Stop) -> Unit,
    onOpenTrip: (departure: Departure, stop: Stop) -> Unit,
    /** ids null = all current service changes. */
    onOpenDisruptions: (ids: List<String>?, subtitle: String?) -> Unit,
    onExit: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val departures by viewModel.departures.collectAsStateWithLifecycle()
    val disruptions by viewModel.disruptions.collectAsStateWithLifecycle()
    var showTimeDialog by rememberSaveable { mutableStateOf(false) }

    BackHandler { if (!viewModel.onBack()) onExit() }

    Column(
        Modifier
            .fillMaxSize()
            .background(FahrplanTheme.colors.background)
            .statusBarsPadding()
            .imePadding(),
    ) {
        SearchInput(
            state = state,
            onFocus = viewModel::onFocus,
            onTextChange = viewModel::onTextChange,
            onClear = viewModel::onClear,
            onSubmit = viewModel::onSubmit,
            onSwap = viewModel::onSwap,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp),
        )
        ToolRow(
            state = state,
            live = state.panel == Panel.DEPARTURES && departures is Load.Data,
            onTimeClick = { showTimeDialog = true },
        )
        // One list for every panel. Rows keep their keys across panels (e.g. favorites on the home screen and
        // in the suggestions), so they stay and slide into place while the other rows fade in or out.
        // Cross-fading two whole lists instead showed both on top of each other for a moment.
        val listState = rememberLazyListState()
        LaunchedEffect(state.panel) { listState.scrollToItem(0) }
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (state.panel) {
                Panel.HOME -> homePanel(state, disruptions, viewModel) { onOpenDisruptions(null, null) }
                Panel.SUGGESTIONS -> suggestionsPanel(state, viewModel)
                Panel.DEPARTURES -> departuresPanel(state, departures, viewModel, onOpenTrip, onOpenDisruptions)
                Panel.ROUTES -> routesPanel(state, viewModel, onOpenRoute)
            }
            item(key = "bottom-spacer") { Spacer(Modifier.navigationBarsPadding().height(24.dp)) }
        }
    }

    if (showTimeDialog) {
        TimeDialog(
            initial = state.time,
            onDismiss = { showTimeDialog = false },
            onApply = {
                viewModel.onTimeChange(it)
                showTimeDialog = false
            },
        )
    }
}

@Composable
private fun ToolRow(state: SearchUiState, live: Boolean, onTimeClick: () -> Unit) {
    val colors = FahrplanTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Chip(
            text = timeChipText(state.time),
            selected = !state.time.isNow,
            onClick = onTimeClick,
            leading = {
                Icon(R.drawable.ic_clock, null, size = 16.dp, tint = if (state.time.isNow) colors.ink else colors.background)
            },
        )
        Spacer(Modifier.weight(1f))
        if (live) {
            LiveDot()
            Spacer(Modifier.width(6.dp))
            Txt(stringResource(R.string.live).uppercase(), style = FahrplanTheme.type.label, color = colors.onTime)
            Spacer(Modifier.width(8.dp))
        }
    }
}

// ---- Panels ----

private fun LazyListScope.homePanel(
    state: SearchUiState,
    disruptions: List<Disruption>,
    viewModel: SearchViewModel,
    onOpenDisruptions: () -> Unit,
) {
    if (disruptions.isNotEmpty()) {
        row("disruptions") {
            DisruptionBanner(stringResource(R.string.home_disruptions, disruptions.size), onOpenDisruptions)
        }
    }
    if (state.savedRoutes.isNotEmpty()) {
        row("label-saved-routes") { SectionLabel(stringResource(R.string.section_saved_routes)) }
        rows(state.savedRoutes, key = { "saved-${it.key}" }) { route ->
            SavedRouteRow(route, onClick = { viewModel.onOpenSavedRoute(route) })
        }
    }
    if (state.savedRoutes.isEmpty() && state.favorites.isEmpty() && state.recents.isEmpty()) {
        row("hint") {
            MessageBlock(
                title = stringResource(R.string.home_hint_title),
                body = stringResource(R.string.home_hint_body),
            )
        }
    }
    stopSection(R.string.section_favorites, state.favorites, state, viewModel)
    stopSection(R.string.section_recent, state.recents, state, viewModel)
    row("disclaimer") {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Txt(
                stringResource(R.string.disclaimer),
                style = FahrplanTheme.type.caption,
                color = FahrplanTheme.colors.inkFaint,
            )
            SourceLinks()
        }
    }
}

/** Links to the official sources of the timetable and map data (required by Play's misleading-claims policy). */
@Composable
private fun SourceLinks() {
    val label = stringResource(R.string.disclaimer_sources)
    val color = FahrplanTheme.colors.inkFaint
    val linkStyles = TextLinkStyles(SpanStyle(color = color, textDecoration = TextDecoration.Underline))
    val sources = listOf(
        "vvo-online.de" to "https://www.vvo-online.de",
        "dvb.de" to "https://www.dvb.de",
        "OpenStreetMap" to "https://www.openstreetmap.org/copyright",
    )
    val text = buildAnnotatedString {
        append(label)
        append(" ")
        sources.forEachIndexed { i, (name, url) ->
            if (i > 0) append(" · ")
            withLink(LinkAnnotation.Url(url, linkStyles)) { append(name) }
        }
    }
    BasicText(text = text, style = FahrplanTheme.type.caption.copy(color = color))
}

private fun LazyListScope.stopSection(title: Int, stops: List<Stop>, state: SearchUiState, viewModel: SearchViewModel) {
    if (stops.isEmpty()) return
    row("label-$title") { SectionLabel(stringResource(title)) }
    rows(stops, key = { "$title-${it.id}" }) { stop ->
        StopRow(
            stop = stop,
            isFavorite = stop.id in state.favoriteIds,
            onClick = { viewModel.onSelect(stop) },
            onToggleFavorite = { viewModel.onToggleFavorite(stop) },
        )
    }
}

private fun LazyListScope.suggestionsPanel(state: SearchUiState, viewModel: SearchViewModel) {
    if (state.focusedText.trim().length < StopRepository.MIN_QUERY_LENGTH) {
        stopSection(R.string.section_favorites, state.favorites, state, viewModel)
        stopSection(R.string.section_recent, state.recents, state, viewModel)
        return
    }
    when (val results = state.results) {
        is Load.Data -> {
            if (results.value.isEmpty()) {
                row("message") { MessageBlock(stringResource(R.string.empty_stops)) }
            } else {
                stopSection(R.string.section_results, results.value, state, viewModel)
            }
        }
        is Load.Failed -> row("error") { ErrorBlock(results.kind, onRetry = viewModel::onRetryStops) }
        Load.Loading, Load.Idle -> row("skeleton") { SkeletonRows(4) }
    }
}

private fun LazyListScope.departuresPanel(
    state: SearchUiState,
    departures: Load<List<Departure>>,
    viewModel: SearchViewModel,
    onOpenTrip: (Departure, Stop) -> Unit,
    onOpenDisruptions: (List<String>?, String?) -> Unit,
) {
    val origin = state.origin ?: return
    row("board-header") { BoardHeader(origin, origin.id in state.favoriteIds) { viewModel.onToggleFavorite(origin) } }
    when (departures) {
        is Load.Data -> {
            val lines = departures.value.map { it.line to it.mode }.distinctBy { it.first }
                .sortedWith(compareBy({ it.first.toIntOrNull() ?: Int.MAX_VALUE }, { it.first }))
            if (lines.size > 1) {
                row("filter") { LineFilter(lines, state.lineFilter, viewModel::onLineFilter) }
            }
            val visible = departures.value.filter { state.lineFilter == null || it.line == state.lineFilter }
            if (visible.isEmpty()) {
                row("message") { MessageBlock(stringResource(R.string.empty_departures)) }
            }
            rows(visible, key = { "${it.tripId}-${it.rawTime}" }) { departure ->
                val now = rememberNow()
                DepartureRow(
                    departure = departure,
                    now = now,
                    onDisruptionClick = { onOpenDisruptions(departure.changeIds, "${departure.line} ${departure.direction}") },
                    onClick = { onOpenTrip(departure, origin) },
                )
                Hairline(start = 86.dp)
            }
        }
        is Load.Failed -> row("error") { ErrorBlock(departures.kind, onRetry = viewModel::onRetry) }
        else -> row("skeleton") { SkeletonRows() }
    }
}

private fun LazyListScope.routesPanel(
    state: SearchUiState,
    viewModel: SearchViewModel,
    onOpenRoute: (Route, Stop, Stop) -> Unit,
) {
    val origin = state.origin ?: return
    val destination = state.destination ?: return
    if (state.sameStop) {
        row("message") { MessageBlock(stringResource(R.string.error_same_stop)) }
        return
    }
    row("routes-header") {
        RoutesHeader(origin, destination, saved = state.isRouteSaved, onToggleSaved = viewModel::onToggleSavedRoute)
    }
    when (val routes = state.routes) {
        is Load.Data -> {
            if (routes.value.routes.isEmpty()) {
                row("message") { MessageBlock(stringResource(R.string.empty_connections)) }
                return
            }
            row("earlier") {
                TextButton(
                    stringResource(R.string.action_earlier),
                    onClick = { viewModel.onMoreRoutes(earlier = true) },
                    enabled = !state.loadingLater,
                    loading = state.loadingEarlier,
                )
                Hairline(start = 0.dp)
            }
            if (state.loadingEarlier) {
                row("earlier-skeleton") { SkeletonRouteRows(2) }
            }
            rows(routes.value.routes, key = { r -> "${r.departure?.scheduled}-${r.legs.joinToString { it.line.orEmpty() }}" }) { route ->
                RouteRow(route) { onOpenRoute(route, origin, destination) }
                Hairline()
            }
            if (state.loadingLater) {
                row("later-skeleton") { SkeletonRouteRows(2) }
            }
            row("later") {
                TextButton(
                    stringResource(R.string.action_later),
                    onClick = { viewModel.onMoreRoutes(earlier = false) },
                    enabled = !state.loadingEarlier,
                    loading = state.loadingLater,
                )
            }
        }
        is Load.Failed -> row("error") { ErrorBlock(routes.kind, onRetry = viewModel::onRetry) }
        else -> row("skeleton") { SkeletonRows(5) }
    }
}

/**
 * A row of the panel list that fades in/out and slides when the rows around it change.
 * Leaving rows vanish quickly and new ones fade in a little later, so rows sliding into place never
 * have a half-transparent row lying on top of them.
 */
private fun LazyListScope.row(key: Any, content: @Composable () -> Unit) =
    item(key = key) { Column(panelRowModifier()) { content() } }

private fun <T> LazyListScope.rows(list: List<T>, key: (T) -> Any, content: @Composable (T) -> Unit) =
    items(list, key = key) { element -> Column(panelRowModifier()) { content(element) } }

private fun androidx.compose.foundation.lazy.LazyItemScope.panelRowModifier() = Modifier.animateItem(
    fadeInSpec = tween(durationMillis = 220, delayMillis = 90),
    placementSpec = spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold),
    fadeOutSpec = tween(durationMillis = 70),
)

// ---- Pieces ----

@Composable
private fun BoardHeader(stop: Stop, isFavorite: Boolean, onToggleFavorite: () -> Unit) {
    val colors = FahrplanTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Txt(stop.displayPlace.uppercase(), style = FahrplanTheme.type.label, color = colors.inkMuted)
            Txt(stop.name, style = FahrplanTheme.type.title, maxLines = 2)
        }
        IconButton(
            icon = if (isFavorite) R.drawable.ic_star else R.drawable.ic_star_outline,
            contentDescription = stringResource(if (isFavorite) R.string.action_unfavorite else R.string.action_favorite),
            onClick = onToggleFavorite,
            tint = if (isFavorite) colors.accent else colors.inkFaint,
        )
    }
}

@Composable
private fun LineFilter(lines: List<Pair<String, markus.wieland.dvbfahrplan.core.model.Mode>>, selected: String?, onSelect: (String?) -> Unit) {
    val colors = FahrplanTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Chip(stringResource(R.string.all_lines), selected = selected == null, onClick = { onSelect(null) })
        lines.forEach { (line, mode) ->
            val isSelected = selected == line
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onSelect(if (isSelected) null else line) }
                    .background(if (isSelected) colors.surfaceSunken else androidx.compose.ui.graphics.Color.Transparent)
                    .padding(4.dp),
            ) {
                LineBadge(line, mode, compact = true)
            }
        }
    }
}

/** "Albertplatz → Hauptbahnhof" above the connections, with a star to remember the route. */
@Composable
private fun RoutesHeader(origin: Stop, destination: Stop, saved: Boolean, onToggleSaved: () -> Unit) {
    val colors = FahrplanTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(
            "${origin.name} → ${destination.name}",
            style = FahrplanTheme.type.heading,
            maxLines = 2,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            icon = if (saved) R.drawable.ic_star else R.drawable.ic_star_outline,
            contentDescription = stringResource(if (saved) R.string.action_unsave_route else R.string.action_save_route),
            onClick = onToggleSaved,
            tint = if (saved) colors.accent else colors.inkFaint,
        )
    }
}

/** A remembered route on the home screen: both stops on the same rail as the input fields. */
@Composable
private fun SavedRouteRow(route: SavedRoute, onClick: () -> Unit) {
    val colors = FahrplanTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Same rail as the input fields: ring = start, dotted line, square = destination.
        Column(
            Modifier
                .weight(1f)
                .drawBehind {
                    val x = RailMark.toPx() / 2
                    val gap = 4.dp.toPx()
                    drawLine(
                        color = colors.inkFaint,
                        start = Offset(x, RailRowHeight.toPx() / 2 + RailMark.toPx() / 2 + gap),
                        end = Offset(x, size.height - RailRowHeight.toPx() / 2 - RailMark.toPx() / 2 - gap),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 5f)),
                    )
                },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RailStop(route.origin.name, isStart = true)
            RailStop(route.destination.name, isStart = false)
        }
        Icon(R.drawable.ic_next, null, tint = colors.inkFaint, size = 20.dp)
    }
    Hairline()
}

private val RailMark = 9.dp
private val RailRowHeight = 22.dp

@Composable
private fun RailStop(name: String, isStart: Boolean) {
    val colors = FahrplanTheme.colors
    Row(Modifier.height(RailRowHeight), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(RailMark)
                .then(
                    if (isStart) Modifier.border(2.dp, colors.ink, CircleShape)
                    else Modifier.background(colors.ink, RoundedCornerShape(2.dp)),
                ),
        )
        Spacer(Modifier.width(14.dp))
        Txt(name, style = FahrplanTheme.type.bodyStrong, maxLines = 1)
    }
}

@Composable
private fun DisruptionBanner(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FahrplanTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(R.drawable.ic_warning, null, tint = colors.delayed, size = 20.dp)
        Spacer(Modifier.width(12.dp))
        Txt(text, style = FahrplanTheme.type.bodyStrong, modifier = Modifier.weight(1f))
        Icon(R.drawable.ic_next, null, tint = colors.inkFaint, size = 20.dp)
    }
}

@Composable
fun ErrorBlock(kind: ErrorKind, onRetry: () -> Unit) {
    MessageBlock(
        title = stringResource(if (kind == ErrorKind.NETWORK) R.string.error_network else R.string.error_generic),
        action = stringResource(R.string.action_retry),
        onAction = onRetry,
    )
}
