package markus.wieland.dvbfahrplan.feature.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
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
        AnimatedContent(
            targetState = state.panel,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "panel",
            modifier = Modifier.weight(1f),
        ) { panel ->
            LazyColumn(Modifier.fillMaxSize()) {
                when (panel) {
                    Panel.HOME -> homePanel(state, disruptions, viewModel) { onOpenDisruptions(null, null) }
                    Panel.SUGGESTIONS -> suggestionsPanel(state, viewModel)
                    Panel.DEPARTURES -> departuresPanel(state, departures, viewModel, onOpenTrip, onOpenDisruptions)
                    Panel.ROUTES -> routesPanel(state, viewModel, onOpenRoute)
                }
                item { Spacer(Modifier.navigationBarsPadding().height(24.dp)) }
            }
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
        item(key = "disruptions") {
            DisruptionBanner(stringResource(R.string.home_disruptions, disruptions.size), onOpenDisruptions)
        }
    }
    if (state.savedRoutes.isNotEmpty()) {
        item(key = "label-saved-routes") { SectionLabel(stringResource(R.string.section_saved_routes)) }
        items(state.savedRoutes, key = { "saved-${it.key}" }) { route ->
            SavedRouteRow(route, onClick = { viewModel.onOpenSavedRoute(route) })
        }
    }
    if (state.savedRoutes.isEmpty() && state.favorites.isEmpty() && state.recents.isEmpty()) {
        item(key = "hint") {
            MessageBlock(
                title = stringResource(R.string.home_hint_title),
                body = stringResource(R.string.home_hint_body),
            )
        }
    }
    stopSection(R.string.section_favorites, state.favorites, state, viewModel)
    stopSection(R.string.section_recent, state.recents, state, viewModel)
    item(key = "disclaimer") {
        Txt(
            stringResource(R.string.disclaimer),
            style = FahrplanTheme.type.caption,
            color = FahrplanTheme.colors.inkFaint,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 28.dp),
        )
    }
}

private fun LazyListScope.stopSection(title: Int, stops: List<Stop>, state: SearchUiState, viewModel: SearchViewModel) {
    if (stops.isEmpty()) return
    item(key = "label-$title") { SectionLabel(stringResource(title)) }
    items(stops, key = { "$title-${it.id}" }) { stop ->
        StopRow(
            stop = stop,
            isFavorite = stop.id in state.favoriteIds,
            onClick = { viewModel.onSelect(stop) },
            onToggleFavorite = { viewModel.onToggleFavorite(stop) },
        )
    }
}

private fun LazyListScope.suggestionsPanel(state: SearchUiState, viewModel: SearchViewModel) {
    if (state.focusedText.trim().length < 2) {
        stopSection(R.string.section_favorites, state.favorites, state, viewModel)
        stopSection(R.string.section_recent, state.recents, state, viewModel)
        return
    }
    when (val results = state.results) {
        is Load.Data -> {
            if (results.value.isEmpty()) {
                item { MessageBlock(stringResource(R.string.empty_stops)) }
            } else {
                stopSection(R.string.section_results, results.value, state, viewModel)
            }
        }
        is Load.Failed -> item { ErrorBlock(results.kind, onRetry = viewModel::onRetryStops) }
        Load.Loading, Load.Idle -> item { SkeletonRows(4) }
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
    item(key = "board-header") { BoardHeader(origin, origin.id in state.favoriteIds) { viewModel.onToggleFavorite(origin) } }
    when (departures) {
        is Load.Data -> {
            val lines = departures.value.map { it.line to it.mode }.distinctBy { it.first }
                .sortedWith(compareBy({ it.first.toIntOrNull() ?: Int.MAX_VALUE }, { it.first }))
            // Service changes the API attached to the departures shown here.
            val changeIds = departures.value.flatMap { it.changeIds }.distinct()
            if (changeIds.isNotEmpty()) {
                item(key = "board-disruptions") {
                    val text = if (changeIds.size == 1) stringResource(R.string.board_disruptions_one)
                    else stringResource(R.string.board_disruptions_many, changeIds.size)
                    DisruptionBanner(text, onClick = { onOpenDisruptions(changeIds, origin.name) }, modifier = Modifier.padding(top = 6.dp))
                }
            }
            if (lines.size > 1) {
                item(key = "filter") { LineFilter(lines, state.lineFilter, viewModel::onLineFilter) }
            }
            val visible = departures.value.filter { state.lineFilter == null || it.line == state.lineFilter }
            if (visible.isEmpty()) {
                item { MessageBlock(stringResource(R.string.empty_departures)) }
            }
            items(visible, key = { "${it.tripId}-${it.rawTime}" }) { departure ->
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
        is Load.Failed -> item { ErrorBlock(departures.kind, onRetry = viewModel::onRetry) }
        else -> item { SkeletonRows() }
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
        item { MessageBlock(stringResource(R.string.error_same_stop)) }
        return
    }
    item(key = "routes-header") {
        RoutesHeader(origin, destination, saved = state.isRouteSaved, onToggleSaved = viewModel::onToggleSavedRoute)
    }
    when (val routes = state.routes) {
        is Load.Data -> {
            if (routes.value.routes.isEmpty()) {
                item { MessageBlock(stringResource(R.string.empty_connections)) }
                return
            }
            item(key = "earlier") {
                TextButton(
                    stringResource(R.string.action_earlier),
                    onClick = { viewModel.onMoreRoutes(earlier = true) },
                    enabled = !state.loadingLater,
                    loading = state.loadingEarlier,
                )
                Hairline(start = 0.dp)
            }
            if (state.loadingEarlier) {
                item(key = "earlier-skeleton") { SkeletonRouteRows(2, Modifier.animateItem()) }
            }
            items(routes.value.routes, key = { r -> "${r.departure?.scheduled}-${r.legs.joinToString { it.line.orEmpty() }}" }) { route ->
                // animateItem: new earlier/later connections fade in and the list slides instead of jumping.
                Column(Modifier.animateItem()) {
                    RouteRow(route) { onOpenRoute(route, origin, destination) }
                    Hairline()
                }
            }
            if (state.loadingLater) {
                item(key = "later-skeleton") { SkeletonRouteRows(2, Modifier.animateItem()) }
            }
            item(key = "later") {
                TextButton(
                    stringResource(R.string.action_later),
                    onClick = { viewModel.onMoreRoutes(earlier = false) },
                    enabled = !state.loadingEarlier,
                    loading = state.loadingLater,
                )
            }
        }
        is Load.Failed -> item { ErrorBlock(routes.kind, onRetry = viewModel::onRetry) }
        else -> item { SkeletonRows(5) }
    }
}

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
        Column(Modifier.weight(1f)) {
            Txt(route.origin.name, style = FahrplanTheme.type.bodyStrong, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt("→ ", style = FahrplanTheme.type.bodyStrong, color = colors.inkFaint)
                Txt(route.destination.name, style = FahrplanTheme.type.bodyStrong, maxLines = 1)
            }
        }
        Icon(R.drawable.ic_next, null, tint = colors.inkFaint, size = 20.dp)
    }
    Hairline()
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
