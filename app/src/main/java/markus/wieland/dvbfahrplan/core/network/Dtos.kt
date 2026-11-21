package markus.wieland.dvbfahrplan.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wire format of the VVO WebAPI (https://webapi.vvo-online.de). Field names are PascalCase on the wire.
// Every field is optional: the API leaves things out freely (e.g. RealTime for cancelled trips).

@Serializable
data class StatusDto(
    @SerialName("Code") val code: String = "",
    @SerialName("Message") val message: String? = null,
)

interface HasStatus {
    val status: StatusDto?
}

@Serializable
data class PlatformDto(
    @SerialName("Name") val name: String? = null,
    @SerialName("Type") val type: String? = null,
)

// ---- tr/pointfinder ----

@Serializable
data class PointFinderRequest(
    val query: String,
    val stopsOnly: Boolean = true,
    val regionalOnly: Boolean = true,
    val limit: Int = 15,
    val format: String = "json",
)

@Serializable
data class PointFinderResponse(
    @SerialName("Status") override val status: StatusDto? = null,
    @SerialName("PointStatus") val pointStatus: String? = null,
    /** Pipe separated: `id|type|place|name|high|right|distance||` */
    @SerialName("Points") val points: List<String> = emptyList(),
) : HasStatus

// ---- dm ----

@Serializable
data class DepartureRequest(
    val stopid: String,
    val limit: Int = 30,
    /** ISO-8601; null = now */
    val time: String? = null,
    val isarrival: Boolean = false,
    val shorttermchanges: Boolean = true,
    val mentzonly: Boolean = false,
    val format: String = "json",
)

@Serializable
data class DepartureMonitorResponse(
    @SerialName("Status") override val status: StatusDto? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("Place") val place: String? = null,
    @SerialName("Departures") val departures: List<DepartureDto> = emptyList(),
) : HasStatus

@Serializable
data class DepartureDto(
    @SerialName("Id") val id: String = "",
    @SerialName("LineName") val lineName: String = "",
    @SerialName("Direction") val direction: String = "",
    @SerialName("Platform") val platform: PlatformDto? = null,
    @SerialName("Mot") val mot: String? = null,
    @SerialName("RealTime") val realTime: String? = null,
    @SerialName("ScheduledTime") val scheduledTime: String = "",
    @SerialName("State") val state: String? = null,
    @SerialName("RouteChanges") val routeChanges: List<String> = emptyList(),
    @SerialName("Occupancy") val occupancy: String? = null,
)

// ---- dm/trip ----

@Serializable
data class TripRequest(
    val tripid: String,
    val time: String,
    val stopid: String,
    val mapdata: Boolean = true,
    val format: String = "json",
)

@Serializable
data class TripResponse(
    @SerialName("Status") override val status: StatusDto? = null,
    @SerialName("Stops") val stops: List<TripStopDto> = emptyList(),
    @SerialName("MapData") val mapData: List<String> = emptyList(),
) : HasStatus

@Serializable
data class TripStopDto(
    @SerialName("Id") val id: String = "",
    @SerialName("Place") val place: String = "",
    @SerialName("Name") val name: String = "",
    /** Actually the GK easting (Rechtswert), despite the name. */
    @SerialName("Longitude") val longitude: Long = 0,
    /** Actually the GK northing (Hochwert), despite the name. */
    @SerialName("Latitude") val latitude: Long = 0,
    @SerialName("Position") val position: String? = null,
    @SerialName("Platform") val platform: PlatformDto? = null,
    @SerialName("Time") val time: String = "",
    @SerialName("RealTime") val realTime: String? = null,
    @SerialName("State") val state: String? = null,
)

// ---- stt/lines ----

@Serializable
data class LinesRequest(val stopid: String, val format: String = "json")

@Serializable
data class LinesResponse(
    @SerialName("Status") override val status: StatusDto? = null,
    @SerialName("Lines") val lines: List<LineDto> = emptyList(),
) : HasStatus

@Serializable
data class LineDto(
    @SerialName("Name") val name: String = "",
    @SerialName("Mot") val mot: String? = null,
    @SerialName("Directions") val directions: List<LineDirectionDto> = emptyList(),
)

@Serializable
data class LineDirectionDto(@SerialName("Name") val name: String = "")

// ---- tr/trips + tr/prevnext ----

@Serializable
data class RouteRequest(
    val origin: String,
    val destination: String,
    val time: String? = null,
    val isarrivaltime: Boolean = false,
    val shorttermchanges: Boolean = true,
    val format: String = "json",
)

@Serializable
data class PrevNextRequest(
    val origin: String,
    val destination: String,
    val sessionId: String,
    val previous: Boolean,
    val time: String? = null,
    val isarrivaltime: Boolean = false,
    val shorttermchanges: Boolean = true,
    val format: String = "json",
)

@Serializable
data class RoutesResponse(
    @SerialName("Status") override val status: StatusDto? = null,
    @SerialName("SessionId") val sessionId: String? = null,
    @SerialName("Routes") val routes: List<RouteDto> = emptyList(),
) : HasStatus

@Serializable
data class RouteDto(
    @SerialName("RouteId") val routeId: Int = 0,
    @SerialName("Duration") val duration: Int = 0,
    @SerialName("Interchanges") val interchanges: Int = 0,
    @SerialName("Price") val price: String? = null,
    @SerialName("FareZoneNames") val fareZoneNames: String? = null,
    @SerialName("PartialRoutes") val partialRoutes: List<PartialRouteDto> = emptyList(),
    @SerialName("MapData") val mapData: List<String> = emptyList(),
)

@Serializable
data class PartialRouteDto(
    @SerialName("Duration") val duration: Int = 0,
    @SerialName("Mot") val mot: MotDto? = null,
    @SerialName("MapDataIndex") val mapDataIndex: Int? = null,
    @SerialName("Infos") val infos: List<String> = emptyList(),
    @SerialName("RegularStops") val regularStops: List<RouteStopDto> = emptyList(),
)

@Serializable
data class MotDto(
    @SerialName("Type") val type: String? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("Direction") val direction: String? = null,
    @SerialName("Changes") val changes: List<String> = emptyList(),
)

@Serializable
data class RouteStopDto(
    @SerialName("DataId") val dataId: String = "",
    @SerialName("Place") val place: String = "",
    @SerialName("Name") val name: String = "",
    @SerialName("Platform") val platform: PlatformDto? = null,
    @SerialName("Latitude") val latitude: Long = 0,
    @SerialName("Longitude") val longitude: Long = 0,
    @SerialName("ArrivalTime") val arrivalTime: String? = null,
    @SerialName("DepartureTime") val departureTime: String? = null,
    @SerialName("ArrivalRealTime") val arrivalRealTime: String? = null,
    @SerialName("DepartureRealTime") val departureRealTime: String? = null,
    @SerialName("ArrivalState") val arrivalState: String? = null,
    @SerialName("DepartureState") val departureState: String? = null,
)

// ---- rc ----

@Serializable
data class RouteChangesRequest(
    val shortterm: Boolean = true,
    val provider: String = "dvb",
    val format: String = "json",
)

@Serializable
data class RouteChangesResponse(
    @SerialName("Status") override val status: StatusDto? = null,
    @SerialName("Changes") val changes: List<RouteChangeDto> = emptyList(),
    @SerialName("Lines") val lines: List<RouteChangeLineDto> = emptyList(),
) : HasStatus

@Serializable
data class RouteChangeDto(
    @SerialName("Id") val id: String = "",
    @SerialName("Type") val type: String? = null,
    @SerialName("Title") val title: String = "",
    @SerialName("Description") val description: String = "",
    @SerialName("ValidityPeriods") val validityPeriods: List<ValidityPeriodDto> = emptyList(),
    @SerialName("LineIds") val lineIds: List<String> = emptyList(),
)

@Serializable
data class ValidityPeriodDto(
    @SerialName("Begin") val begin: String? = null,
    @SerialName("End") val end: String? = null,
)

@Serializable
data class RouteChangeLineDto(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String = "",
    @SerialName("Mot") val mot: String? = null,
)
