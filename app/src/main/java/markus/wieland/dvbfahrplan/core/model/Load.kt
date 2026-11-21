package markus.wieland.dvbfahrplan.core.model

import markus.wieland.dvbfahrplan.core.network.VvoException
import java.time.Instant

/** Loading state of one piece of remote data. */
sealed interface Load<out T> {
    data object Idle : Load<Nothing>
    data object Loading : Load<Nothing>
    data class Data<T>(val value: T, val updatedAt: Instant) : Load<T>
    data class Failed(val kind: ErrorKind) : Load<Nothing>

    val valueOrNull: T? get() = (this as? Data<T>)?.value
}

enum class ErrorKind { NETWORK, OTHER }

fun Throwable.toErrorKind(): ErrorKind = if (this is VvoException.Network) ErrorKind.NETWORK else ErrorKind.OTHER
