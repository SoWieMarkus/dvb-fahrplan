package markus.wieland.dvbfahrplan.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import markus.wieland.dvbfahrplan.core.model.Departure
import markus.wieland.dvbfahrplan.core.model.Disruption
import markus.wieland.dvbfahrplan.core.model.Load
import markus.wieland.dvbfahrplan.core.model.RouteSearch
import markus.wieland.dvbfahrplan.core.model.SavedRoute
import markus.wieland.dvbfahrplan.core.model.Stop
import markus.wieland.dvbfahrplan.core.model.toErrorKind
import markus.wieland.dvbfahrplan.data.SavedRouteRepository
import markus.wieland.dvbfahrplan.data.SearchTime
import markus.wieland.dvbfahrplan.data.StopRepository
import markus.wieland.dvbfahrplan.data.TransitRepository
import markus.wieland.dvbfahrplan.data.attempt
import markus.wieland.dvbfahrplan.data.poll
import java.time.Clock
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

enum class Field { ORIGIN, DESTINATION }

/** What the area below the two input fields shows. Derived from the input state, never stored. */
enum class Panel { HOME, SUGGESTIONS, DEPARTURES, ROUTES }

data class SearchUiState(
    val originText: String = "",
    val destinationText: String = "",
    val origin: Stop? = null,
    val destination: Stop? = null,
    val focused: Field? = null,
    val time: SearchTime = SearchTime(),
    val results: Load<List<Stop>> = Load.Idle,
    val favorites: List<Stop> = emptyList(),
    val recents: List<Stop> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),
    val savedRoutes: List<SavedRoute> = emptyList(),
    val lineFilter: String? = null,
    val routes: Load<RouteSearch> = Load.Idle,
    val loadingEarlier: Boolean = false,
    val loadingLater: Boolean = false,
    /** Bumped by "try again" to restart the loaders. */
    val refresh: Int = 0,
) {
    val panel: Panel
        get() = when {
            focused != null -> Panel.SUGGESTIONS
            origin != null && destination != null -> Panel.ROUTES
            origin != null -> Panel.DEPARTURES
            else -> Panel.HOME
        }

    val focusedText: String
        get() = when (focused) {
            Field.ORIGIN -> originText
            Field.DESTINATION -> destinationText
            null -> ""
        }

    val sameStop: Boolean get() = origin != null && origin.id == destination?.id

    /** The current origin → destination pair is remembered. */
    val isRouteSaved: Boolean
        get() = origin != null && destination != null &&
            savedRoutes.any { it.origin.id == origin.id && it.destination.id == destination.id }
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val stops: StopRepository,
    private val savedRouteRepository: SavedRouteRepository,
    private val transit: TransitRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private data class BoardKey(val stopId: String, val time: SearchTime, val refresh: Int)

    private var cachedBoard: Pair<BoardKey, Load.Data<List<Departure>>>? = null

    /** Live departure board of the origin; only polls while the UI collects it. */
    val departures: StateFlow<Load<List<Departure>>> = _state
        .map { s -> if (s.destination == null) s.origin?.id?.let { BoardKey(it, s.time, s.refresh) } else null }
        .distinctUntilChanged()
        .flatMapLatest { key ->
            if (key == null) flowOf(Load.Idle)
            else {
                val initial = cachedBoard?.takeIf { it.first.stopId == key.stopId && it.first.time == key.time }?.second
                poll(30.seconds, clock, initial = initial) { transit.departures(key.stopId, key.time) }.map { load -> load.also { if (it is Load.Data) cachedBoard = key to it } }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Load.Idle)

    /** Current service changes, refreshed every few minutes while visible. */
    val disruptions: StateFlow<List<Disruption>> = poll(5.minutes, clock) { transit.disruptions() }
        .map { load -> load.valueOrNull?.filter { it.isActive(clock.instant()) }.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var routeJob: Job? = null

    init {
        viewModelScope.launch {
            combine(stops.favorites, stops.recents, stops.favoriteIds) { favorites, recents, ids ->
                Triple(favorites, recents, ids)
            }.collect { (favorites, recents, ids) ->
                _state.update { it.copy(favorites = favorites, recents = recents, favoriteIds = ids) }
            }
        }

        viewModelScope.launch {
            savedRouteRepository.savedRoutes.collect { routes -> _state.update { it.copy(savedRoutes = routes) } }
        }

        // Stop suggestions. debounce + collectLatest: a newer query cancels the running request,
        // so an old, slower answer can never overwrite the results for what is typed now.
        viewModelScope.launch {
            _state
                .map { s -> s.focused?.let { s.focusedText.trim() }.orEmpty() }
                .distinctUntilChanged()
                .debounce(250)
                .collect { query -> searchStops(query) }
        }

        viewModelScope.launch {
            _state
                .map { s -> listOf(s.origin?.id, s.destination?.id, s.time, s.refresh) }
                .distinctUntilChanged()
                .collect { searchRoutes(it[0] as String?, it[1] as String?, it[2] as SearchTime) }
        }
    }

    private var stopSearchJob: Job? = null

    private fun searchStops(query: String) {
        stopSearchJob?.cancel()
        if (query.length < 2) {
            _state.update { it.copy(results = Load.Idle) }
            return
        }
        stopSearchJob = viewModelScope.launch {
            _state.update { it.copy(results = Load.Loading) }
            val result = attempt { stops.search(query) }
            _state.update {
                it.copy(results = result.fold({ list -> Load.Data(list, clock.instant()) }, { e -> Load.Failed(e.toErrorKind()) }))
            }
        }
    }

    private fun searchRoutes(originId: String?, destinationId: String?, time: SearchTime) {
        routeJob?.cancel()
        if (originId == null || destinationId == null || originId == destinationId) {
            _state.update { it.copy(routes = Load.Idle, loadingEarlier = false, loadingLater = false) }
            return
        }
        routeJob = viewModelScope.launch {
            _state.update { it.copy(routes = Load.Loading, loadingEarlier = false, loadingLater = false) }
            val result = attempt { transit.routes(originId, destinationId, time) }
            _state.update {
                it.copy(routes = result.fold({ r -> Load.Data(r, clock.instant()) }, { e -> Load.Failed(e.toErrorKind()) }))
            }
        }
    }

    // ---- Input events ----

    fun onFocus(field: Field) = _state.update { it.copy(focused = field) }

    fun onTextChange(field: Field, text: String) = _state.update { s ->
        when (field) {
            Field.ORIGIN -> s.copy(originText = text, origin = s.origin?.takeIf { it.name == text })
            Field.DESTINATION -> s.copy(destinationText = text, destination = s.destination?.takeIf { it.name == text })
        }
    }

    /** Leaves the text fields; a half-typed name falls back to the stop that was selected before. */
    fun onDismissInput() = _state.update { s ->
        s.copy(
            focused = null,
            originText = s.origin?.name.orEmpty(),
            destinationText = s.destination?.name.orEmpty(),
        )
    }

    /** Keyboard "go": take the best match of the current search. */
    fun onSubmit() {
        val first = _state.value.results.valueOrNull?.firstOrNull() ?: return onDismissInput()
        onSelect(first)
    }

    fun onSelect(stop: Stop) {
        val s = _state.value
        val field = s.focused ?: if (s.origin == null) Field.ORIGIN else Field.DESTINATION
        _state.update {
            when (field) {
                Field.ORIGIN -> it.copy(origin = stop, originText = stop.name, lineFilter = null)
                Field.DESTINATION -> it.copy(destination = stop, destinationText = stop.name)
            }.copy(focused = null, results = Load.Idle)
        }
        viewModelScope.launch { stops.markUsed(stop) }
    }

    fun onClear(field: Field) = _state.update { s ->
        when (field) {
            Field.ORIGIN -> s.copy(origin = null, originText = "", focused = Field.ORIGIN, lineFilter = null)
            Field.DESTINATION -> s.copy(destination = null, destinationText = "")
        }
    }

    fun onSwap() = _state.update { s ->
        s.copy(
            origin = s.destination,
            destination = s.origin,
            originText = s.destinationText,
            destinationText = s.originText,
            lineFilter = null,
        )
    }

    fun onTimeChange(time: SearchTime) = _state.update { it.copy(time = time) }

    fun onLineFilter(line: String?) = _state.update { it.copy(lineFilter = line) }

    fun onToggleSavedRoute() {
        val s = _state.value
        val origin = s.origin ?: return
        val destination = s.destination ?: return
        viewModelScope.launch { savedRouteRepository.toggle(origin, destination) }
    }

    /** Opens a remembered route: fills both fields, which shows its current connections. */
    fun onOpenSavedRoute(route: SavedRoute) {
        _state.update {
            it.copy(
                origin = route.origin,
                originText = route.origin.name,
                destination = route.destination,
                destinationText = route.destination.name,
                focused = null,
                lineFilter = null,
            )
        }
    }

    fun onToggleFavorite(stop: Stop) {
        viewModelScope.launch { stops.toggleFavorite(stop) }
    }

    /** "Try again" after an error: restarts departures and connections. */
    fun onRetry() = _state.update { it.copy(refresh = it.refresh + 1) }

    fun onRetryStops() = searchStops(_state.value.focusedText.trim())

    /** Earlier / later connections: the server continues the search it stored under the session id. */
    fun onMoreRoutes(earlier: Boolean) {
        val s = _state.value
        val current = s.routes.valueOrNull ?: return
        val sessionId = current.sessionId ?: return
        val origin = s.origin ?: return
        val destination = s.destination ?: return
        if (s.loadingEarlier || s.loadingLater) return
        _state.update { if (earlier) it.copy(loadingEarlier = true) else it.copy(loadingLater = true) }
        routeJob = viewModelScope.launch {
            val result = attempt { transit.moreRoutes(origin.id, destination.id, s.time, sessionId, earlier) }
            _state.update { state ->
                val merged = result.getOrNull()?.let { more ->
                    val routes = (more.routes.takeIf { it.isNotEmpty() } ?: current.routes)
                        .distinctBy { route -> route.departure?.scheduled to route.legs.map { it.line } }
                        .sortedBy { it.departure?.scheduled }
                    Load.Data(RouteSearch(more.sessionId ?: sessionId, routes), clock.instant())
                }
                state.copy(routes = merged ?: state.routes, loadingEarlier = false, loadingLater = false)
            }
        }
    }

    /**
     * Back press inside the search screen, from the most specific to the least specific state.
     * @return false when there is nothing left to undo (the app may close).
     */
    fun onBack(): Boolean {
        val s = _state.value
        when {
            s.focused != null -> onDismissInput()
            s.destination != null || s.destinationText.isNotEmpty() -> onClear(Field.DESTINATION)
            s.origin != null -> _state.update { it.copy(origin = null, originText = "", lineFilter = null) }
            else -> return false
        }
        return true
    }
}
