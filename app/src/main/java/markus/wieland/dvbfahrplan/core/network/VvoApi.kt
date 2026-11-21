package markus.wieland.dvbfahrplan.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resumeWithException

sealed class VvoException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** No connection, timeout, ... */
    class Network(cause: IOException) : VvoException(cause.message ?: "network error", cause)

    /** HTTP status outside 2xx (e.g. 400 ValidationError). */
    class Http(val code: Int) : VvoException("HTTP $code")

    /** HTTP 200 but `Status.Code` is not `Ok` (e.g. `ServiceError` for an unknown stop). */
    class Service(val code: String, message: String?) : VvoException("$code: ${message.orEmpty()}")
}

/**
 * Thin client for the VVO WebAPI. Every endpoint is a POST with a JSON body.
 * Calls are cancelled together with the calling coroutine, so a `mapLatest` on a search
 * flow really aborts the stale request instead of letting it race the new one.
 */
class VvoApi(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl = DEFAULT_BASE_URL.toHttpUrl(),
    @PublishedApi internal val json: Json = DefaultJson,
) {

    suspend fun findStops(query: String) =
        post<PointFinderRequest, PointFinderResponse>("tr/pointfinder", PointFinderRequest(query))

    suspend fun departures(request: DepartureRequest) =
        post<DepartureRequest, DepartureMonitorResponse>("dm", request)

    suspend fun trip(request: TripRequest) = post<TripRequest, TripResponse>("dm/trip", request)

    suspend fun lines(stopId: String) = post<LinesRequest, LinesResponse>("stt/lines", LinesRequest(stopId))

    suspend fun routes(request: RouteRequest) = post<RouteRequest, RoutesResponse>("tr/trips", request)

    suspend fun moreRoutes(request: PrevNextRequest) =
        post<PrevNextRequest, RoutesResponse>("tr/prevnext", request)

    suspend fun routeChanges() =
        post<RouteChangesRequest, RouteChangesResponse>("rc", RouteChangesRequest())

    private suspend inline fun <reified Req, reified Res : HasStatus> post(path: String, body: Req): Res =
        execute(path, json.encodeToString(serializer<Req>(), body), serializer<Res>())

    @PublishedApi
    internal suspend fun <Res : HasStatus> execute(path: String, body: String, deserializer: KSerializer<Res>): Res = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(baseUrl.resolve(path)!!)
            // Without an explicit Accept header the server may pick its HTML "snapshot" view.
            .header("Accept", "application/json")
            .post(body.toRequestBody(JSON))
            .build()
        val text = client.newCall(request).await().use { response ->
            if (!response.isSuccessful) throw VvoException.Http(response.code)
            response.body?.string().orEmpty()
        }
        val result = json.decodeFromString(deserializer, text)
        val status = result.status
        if (status != null && !status.code.equals("Ok", ignoreCase = true)) {
            throw VvoException.Service(status.code, status.message)
        }
        result
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://webapi.vvo-online.de/"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        val DefaultJson = Json {
            ignoreUnknownKeys = true
            // Send defaults like "format":"json" – without it some endpoints answer with HTML.
            encodeDefaults = true
            coerceInputValues = true
            explicitNulls = false
        }
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { _, _, _ -> response.close() }
        }

        override fun onFailure(call: Call, e: IOException) {
            if (!continuation.isCancelled) continuation.resumeWithException(VvoException.Network(e))
        }
    })
}
