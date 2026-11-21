package markus.wieland.dvbfahrplan.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import markus.wieland.dvbfahrplan.core.database.SavedRouteDao
import markus.wieland.dvbfahrplan.core.database.StopDao
import markus.wieland.dvbfahrplan.core.model.Departure
import markus.wieland.dvbfahrplan.core.model.Disruption
import markus.wieland.dvbfahrplan.core.model.LineInfo
import markus.wieland.dvbfahrplan.core.model.RouteSearch
import markus.wieland.dvbfahrplan.core.model.SavedRoute
import markus.wieland.dvbfahrplan.core.model.Stop
import markus.wieland.dvbfahrplan.core.model.Trip
import markus.wieland.dvbfahrplan.core.network.DepartureRequest
import markus.wieland.dvbfahrplan.core.network.PrevNextRequest
import markus.wieland.dvbfahrplan.core.network.RouteRequest
import markus.wieland.dvbfahrplan.core.network.TripRequest
import markus.wieland.dvbfahrplan.core.network.VvoApi
import markus.wieland.dvbfahrplan.core.network.VvoDate
import markus.wieland.dvbfahrplan.core.network.VvoException
import markus.wieland.dvbfahrplan.core.network.toDepartures
import markus.wieland.dvbfahrplan.core.network.toDisruptions
import markus.wieland.dvbfahrplan.core.network.toLines
import markus.wieland.dvbfahrplan.core.network.toRouteSearch
import markus.wieland.dvbfahrplan.core.network.toStops
import markus.wieland.dvbfahrplan.core.network.toTrip
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** When a search refers to: leave now, leave at, or arrive by. */
data class SearchTime(val instant: Instant? = null, val isArrival: Boolean = false) {
    val isNow: Boolean get() = instant == null
}

@Singleton
class StopRepository @Inject constructor(
    private val api: VvoApi,
    private val dao: StopDao,
    private val clock: Clock,
) {
    /**
     * Stops matching [query]. The API rejects queries shorter than [MIN_QUERY_LENGTH] with a ValidationError;
     * that simply means "nothing found", not a failure.
     */
    suspend fun search(query: String): List<Stop> {
        if (query.trim().length < MIN_QUERY_LENGTH) return emptyList()
        return try {
            api.findStops(query.trim()).toStops()
        } catch (e: VvoException.Service) {
            if (e.code.equals("ValidationError", ignoreCase = true)) emptyList() else throw e
        }
    }

    companion object {
        const val MIN_QUERY_LENGTH = 3
    }

    val favorites: Flow<List<Stop>> = dao.favorites().map { list -> list.map { it.toStop() } }
    val recents: Flow<List<Stop>> = dao.recents(limit = 8).map { list -> list.map { it.toStop() } }
    val favoriteIds: Flow<Set<String>> = dao.favoriteIds().map { it.toSet() }

    suspend fun markUsed(stop: Stop) = dao.markUsed(stop, clock.millis())
    suspend fun toggleFavorite(stop: Stop) = dao.toggleFavorite(stop)
}

@Singleton
class SavedRouteRepository @Inject constructor(
    private val dao: SavedRouteDao,
    private val clock: Clock,
) {
    val savedRoutes: Flow<List<SavedRoute>> = dao.all().map { list -> list.map { it.toSavedRoute() } }

    suspend fun toggle(origin: Stop, destination: Stop) = dao.toggle(origin, destination, clock.millis())
}

@Singleton
class TransitRepository @Inject constructor(private val api: VvoApi) {

    suspend fun departures(stopId: String, time: SearchTime = SearchTime()): List<Departure> =
        api.departures(
            DepartureRequest(
                stopid = stopId,
                time = time.instant?.let(VvoDate::format),
                isarrival = time.isArrival,
            ),
        ).toDepartures()

    suspend fun lines(stopId: String): List<LineInfo> = api.lines(stopId).toLines()

    suspend fun trip(departure: Departure, stopId: String): Trip =
        api.trip(TripRequest(tripid = departure.tripId, time = departure.rawTime, stopid = stopId)).toTrip()

    suspend fun routes(originId: String, destinationId: String, time: SearchTime): RouteSearch =
        api.routes(
            RouteRequest(
                origin = originId,
                destination = destinationId,
                time = time.instant?.let(VvoDate::format),
                isarrivaltime = time.isArrival,
            ),
        ).toRouteSearch()

    /**
     * Continues a search: the server remembers it under [sessionId] and answers with all routes so far
     * plus the newly found earlier ([earlier] = true) or later ones.
     */
    suspend fun moreRoutes(
        originId: String,
        destinationId: String,
        time: SearchTime,
        sessionId: String,
        earlier: Boolean,
    ): RouteSearch = api.moreRoutes(
        PrevNextRequest(
            origin = originId,
            destination = destinationId,
            sessionId = sessionId,
            previous = earlier,
            time = time.instant?.let(VvoDate::format),
            isarrivaltime = time.isArrival,
        ),
    ).toRouteSearch()

    suspend fun disruptions(): List<Disruption> = api.routeChanges().toDisruptions()
}
