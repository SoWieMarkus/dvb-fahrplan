package markus.wieland.dvbfahrplan.data

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import markus.wieland.dvbfahrplan.core.model.Load
import markus.wieland.dvbfahrplan.core.model.toErrorKind
import java.time.Clock
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration

/** Like runCatching, but never swallows coroutine cancellation. */
suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Log.w("Fahrplan", "request failed", e)
    Result.failure(e)
}

/**
 * "Live" data: the VVO API has no push channel, so we simply ask again every [interval]
 * for as long as somebody collects (the UI stops collecting when the screen is not visible).
 * A failed refresh keeps the last good data instead of replacing it with an error.
 */
fun <T> poll(interval: Duration, clock: Clock, initial: Load.Data<T>? = null, load: suspend () -> T): Flow<Load<T>> = flow {
    var last: Load.Data<T>? = initial
    emit(last ?: Load.Loading)
    while (true) {
        attempt { load() }
            .onSuccess { value -> last = Load.Data(value, clock.instant()).also { emit(it) } }
            .onFailure { error -> if (last == null) emit(Load.Failed(error.toErrorKind())) }
        delay(interval)
    }
}
