package markus.wieland.dvbfahrplan.core.geo

import markus.wieland.dvbfahrplan.core.model.MapSegment
import markus.wieland.dvbfahrplan.core.model.Mode

/**
 * Parses the `MapData` strings of `tr/trips` and `dm/trip`:
 * `"<Mode>|<high>|<right>|<high>|<right>|..."` with Gauss-Krüger coordinates, northing first.
 */
object MapDataParser {

    fun parse(raw: String): MapSegment? {
        val parts = raw.split('|')
        if (parts.size < 3) return null
        val mode = Mode.fromApi(parts.first())
        val numbers = parts.drop(1).mapNotNull { it.toDoubleOrNull() }
        val points = numbers.chunked(2)
            .filter { it.size == 2 }
            .mapNotNull { (high, right) -> GaussKrueger.toWgs84(right = right, high = high) }
        return if (points.isEmpty()) null else MapSegment(mode, points)
    }

    fun parseAll(raw: List<String>?): List<MapSegment?> = raw.orEmpty().map(::parse)
}
