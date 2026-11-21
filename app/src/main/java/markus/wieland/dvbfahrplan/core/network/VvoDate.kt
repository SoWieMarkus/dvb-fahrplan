package markus.wieland.dvbfahrplan.core.network

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The API uses the old Microsoft JSON date format: `/Date(1795276140000-0000)/`.
 * The number is epoch milliseconds (UTC); the offset suffix is informational only.
 */
object VvoDate {
    // Tolerates the JSON-escaped form `\/Date(...)\/` as well.
    private val pattern = Regex("""Date\((-?\d+)([+-]\d{4})?\)""")

    fun parse(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        val match = pattern.find(value) ?: return null
        return Instant.ofEpochMilli(match.groupValues[1].toLong())
    }

    /** Request times are accepted as ISO-8601 with offset. */
    fun format(instant: Instant, zone: ZoneId = ZoneId.of("Europe/Berlin")): String =
        OffsetDateTime.ofInstant(instant, zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}
