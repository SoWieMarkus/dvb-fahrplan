package markus.wieland.dvbfahrplan.core.model

import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

data class LatLng(val lat: Double, val lng: Double)

data class Stop(
    val id: String,
    val name: String,
    /** Town, empty for Dresden itself. */
    val place: String,
    val location: LatLng?,
) {
    val displayPlace: String get() = place.ifBlank { "Dresden" }
}

/** A remembered origin → destination pair ("my way to work"). */
data class SavedRoute(val origin: Stop, val destination: Stop) {
    val key: String get() = "${origin.id}>${destination.id}"
}

data class Platform(val name: String, val isTrack: Boolean)

enum class RealtimeState { ON_TIME, DELAYED, CANCELLED, UNKNOWN }

/** A planned time plus the live prognosis, if the API delivered one. */
data class TimePoint(
    val scheduled: Instant,
    val realTime: Instant?,
    val state: RealtimeState = RealtimeState.UNKNOWN,
) {
    val best: Instant get() = realTime ?: scheduled
    val isCancelled: Boolean get() = state == RealtimeState.CANCELLED

    /** Delay in whole minutes (negative = early), null without live data. */
    val delayMinutes: Int?
        get() = realTime?.let { (Duration.between(scheduled, it).seconds / 60.0).roundToInt() }
}

enum class Occupancy { UNKNOWN, MANY_SEATS, STANDING_ONLY, FULL }

data class Departure(
    /** Trip id; shared by all runs of a line+direction, so a trip is only unique together with [rawTime]. */
    val tripId: String,
    val line: String,
    val direction: String,
    val mode: Mode,
    val platform: Platform?,
    val time: TimePoint,
    val occupancy: Occupancy,
    val changeIds: List<String>,
    /** The scheduled time exactly as the API sent it; `dm/trip` wants it back verbatim. */
    val rawTime: String,
)

data class LineInfo(
    val name: String,
    val mode: Mode,
    val directions: List<String>,
)

enum class TripPosition { PREVIOUS, CURRENT, NEXT, ONWARD }

data class TripStop(
    val id: String,
    val name: String,
    val place: String,
    val platform: Platform?,
    val time: TimePoint,
    val position: TripPosition,
    val location: LatLng?,
)

data class MapSegment(val mode: Mode, val points: List<LatLng>)

data class Trip(val stops: List<TripStop>, val path: List<MapSegment>)

data class RouteStop(
    val id: String,
    val name: String,
    val place: String,
    val platform: Platform?,
    val arrival: TimePoint?,
    val departure: TimePoint?,
    val location: LatLng?,
)

data class Leg(
    val mode: Mode,
    val line: String?,
    val direction: String?,
    val durationMinutes: Int,
    val stops: List<RouteStop>,
    val infos: List<String>,
    val mapDataIndex: Int?,
    val changeIds: List<String>,
) {
    val origin: RouteStop? get() = stops.firstOrNull()
    val destination: RouteStop? get() = stops.lastOrNull()
    val intermediateStops: List<RouteStop> get() = if (stops.size > 2) stops.subList(1, stops.size - 1) else emptyList()
}

data class Route(
    val id: Int,
    val durationMinutes: Int,
    val interchanges: Int,
    val legs: List<Leg>,
    val path: List<MapSegment>,
    val price: String?,
    val fareZones: String?,
) {
    val departure: TimePoint? get() = legs.firstNotNullOfOrNull { leg -> leg.origin?.departure }
    val arrival: TimePoint? get() = legs.lastOrNull { it.destination?.arrival != null }?.destination?.arrival
    val transitLegs: List<Leg> get() = legs.filter { it.mode.isTransit }
    val isCancelled: Boolean
        get() = legs.any { leg -> leg.stops.any { it.departure?.isCancelled == true || it.arrival?.isCancelled == true } }
}

/** Result of a connection search. [sessionId] lets the server continue it (earlier / later connections). */
data class RouteSearch(val sessionId: String?, val routes: List<Route>)

data class LineRef(val name: String, val mode: Mode)

data class Disruption(
    val id: String,
    val title: String,
    val descriptionHtml: String,
    val lines: List<LineRef>,
    val validity: List<ClosedRange<Instant>>,
) {
    fun isActive(at: Instant): Boolean = validity.isEmpty() || validity.any { at in it }
}
