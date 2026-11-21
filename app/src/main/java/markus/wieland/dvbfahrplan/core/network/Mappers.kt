package markus.wieland.dvbfahrplan.core.network

import markus.wieland.dvbfahrplan.core.geo.GaussKrueger
import markus.wieland.dvbfahrplan.core.geo.MapDataParser
import markus.wieland.dvbfahrplan.core.model.Departure
import markus.wieland.dvbfahrplan.core.model.Disruption
import markus.wieland.dvbfahrplan.core.model.Leg
import markus.wieland.dvbfahrplan.core.model.LineInfo
import markus.wieland.dvbfahrplan.core.model.Mode
import markus.wieland.dvbfahrplan.core.model.Occupancy
import markus.wieland.dvbfahrplan.core.model.Platform
import markus.wieland.dvbfahrplan.core.model.RealtimeState
import markus.wieland.dvbfahrplan.core.model.Route
import markus.wieland.dvbfahrplan.core.model.RouteSearch
import markus.wieland.dvbfahrplan.core.model.RouteStop
import markus.wieland.dvbfahrplan.core.model.Stop
import markus.wieland.dvbfahrplan.core.model.TimePoint
import markus.wieland.dvbfahrplan.core.model.Trip
import markus.wieland.dvbfahrplan.core.model.TripPosition
import markus.wieland.dvbfahrplan.core.model.TripStop

/** `id|type|place|name|high|right|distance||` → [Stop]. Non-stop points (addresses, POIs) have a type and are skipped. */
fun parsePoint(raw: String): Stop? {
    val parts = raw.split('|')
    if (parts.size < 6) return null
    val id = parts[0]
    if (id.isBlank() || parts[1].isNotBlank()) return null
    val high = parts[4].toLongOrNull()
    val right = parts[5].toLongOrNull()
    val location = if (high != null && right != null) GaussKrueger.toWgs84(right = right, high = high) else null
    return Stop(id = id, name = parts[3], place = parts[2], location = location)
}

fun PointFinderResponse.toStops(): List<Stop> = points.mapNotNull(::parsePoint)

internal fun String?.toRealtimeState(): RealtimeState = when (this?.lowercase()) {
    "intime" -> RealtimeState.ON_TIME
    "delayed" -> RealtimeState.DELAYED
    "cancelled" -> RealtimeState.CANCELLED
    else -> RealtimeState.UNKNOWN
}

private fun String?.toOccupancy(): Occupancy = when (this?.lowercase()) {
    "manyseats" -> Occupancy.MANY_SEATS
    "standingonly" -> Occupancy.STANDING_ONLY
    "full" -> Occupancy.FULL
    else -> Occupancy.UNKNOWN
}

private fun PlatformDto?.toPlatform(): Platform? {
    val name = this?.name?.takeIf { it.isNotBlank() } ?: return null
    return Platform(name, isTrack = type.equals("Railtrack", ignoreCase = true))
}

private fun timePoint(scheduled: String?, realTime: String?, state: String?): TimePoint? {
    val planned = VvoDate.parse(scheduled) ?: return null
    return TimePoint(planned, VvoDate.parse(realTime), state.toRealtimeState())
}

fun DepartureMonitorResponse.toDepartures(): List<Departure> = departures.mapNotNull { dto ->
    val time = timePoint(dto.scheduledTime, dto.realTime, dto.state) ?: return@mapNotNull null
    Departure(
        tripId = dto.id,
        line = dto.lineName,
        direction = dto.direction,
        mode = Mode.fromApi(dto.mot),
        platform = dto.platform.toPlatform(),
        time = time,
        occupancy = dto.occupancy.toOccupancy(),
        changeIds = dto.routeChanges,
        rawTime = dto.scheduledTime,
    )
}

fun LinesResponse.toLines(): List<LineInfo> = lines.map { line ->
    LineInfo(line.name, Mode.fromApi(line.mot), line.directions.map { it.name })
}

private fun String?.toTripPosition(): TripPosition = when (this?.lowercase()) {
    "previous" -> TripPosition.PREVIOUS
    "current" -> TripPosition.CURRENT
    "next" -> TripPosition.NEXT
    else -> TripPosition.ONWARD
}

fun TripResponse.toTrip(): Trip = Trip(
    stops = stops.mapNotNull { dto ->
        val time = timePoint(dto.time, dto.realTime, dto.state) ?: return@mapNotNull null
        TripStop(
            id = dto.id,
            name = dto.name,
            place = dto.place,
            platform = dto.platform.toPlatform(),
            time = time,
            position = dto.position.toTripPosition(),
            location = GaussKrueger.toWgs84(right = dto.longitude, high = dto.latitude),
        )
    },
    path = MapDataParser.parseAll(mapData).filterNotNull(),
)

private fun RouteStopDto.toRouteStop() = RouteStop(
    id = dataId,
    name = name,
    place = place,
    platform = platform.toPlatform(),
    arrival = timePoint(arrivalTime, arrivalRealTime, arrivalState),
    departure = timePoint(departureTime, departureRealTime, departureState),
    location = GaussKrueger.toWgs84(right = longitude, high = latitude),
)

fun RouteDto.toRoute(): Route = Route(
    id = routeId,
    durationMinutes = duration,
    interchanges = interchanges,
    legs = partialRoutes.map { partial ->
        Leg(
            mode = Mode.fromApi(partial.mot?.type),
            line = partial.mot?.name?.takeIf { it.isNotBlank() },
            direction = partial.mot?.direction?.takeIf { it.isNotBlank() },
            durationMinutes = partial.duration,
            stops = partial.regularStops.map { it.toRouteStop() },
            infos = partial.infos,
            mapDataIndex = partial.mapDataIndex,
            changeIds = partial.mot?.changes.orEmpty(),
        )
    },
    path = MapDataParser.parseAll(mapData).filterNotNull(),
    price = price?.takeIf { it.isNotBlank() },
    fareZones = fareZoneNames?.takeIf { it.isNotBlank() },
)

fun RoutesResponse.toRouteSearch(): RouteSearch = RouteSearch(sessionId, routes.map { it.toRoute() })

fun RouteChangesResponse.toDisruptions(): List<Disruption> {
    val lineRefs = lines.associate { it.id to markus.wieland.dvbfahrplan.core.model.LineRef(it.name, Mode.fromApi(it.mot)) }
    return changes.map { change ->
        Disruption(
            id = change.id,
            title = change.title,
            descriptionHtml = change.description,
            lines = change.lineIds.mapNotNull { lineRefs[it] }.distinct(),
            validity = change.validityPeriods.mapNotNull { period ->
                val begin = VvoDate.parse(period.begin) ?: return@mapNotNull null
                val end = VvoDate.parse(period.end) ?: java.time.Instant.MAX
                begin..end
            },
        )
    }
}
