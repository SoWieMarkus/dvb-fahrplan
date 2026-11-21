package markus.wieland.dvbfahrplan.core.geo

import markus.wieland.dvbfahrplan.core.model.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GaussKruegerTest {

    @Test
    fun `Dresden Hauptbahnhof converts to its WGS84 position`() {
        // Values as returned by tr/trips for stop 33000028 (Hauptbahnhof, platform 3)
        val position = GaussKrueger.toWgs84(right = 4621685L, high = 5657497L)
        assertNotNull(position)
        assertEquals(51.0404, position!!.lat, 0.002)
        assertEquals(13.7320, position.lng, 0.002)
    }

    @Test
    fun `Albertplatz converts to its WGS84 position`() {
        val position = GaussKrueger.toWgs84(right = 4622549L, high = 5660141L)!!
        assertEquals(51.0635, position.lat, 0.002)
        assertEquals(13.7457, position.lng, 0.002)
    }

    @Test
    fun `placeholder zero coordinates are rejected`() {
        assertNull(GaussKrueger.toWgs84(right = 0L, high = 0L))
    }

    @Test
    fun `map data is parsed northing first`() {
        val segment = MapDataParser.parse("Tram|5657498|4621684|5660141|4622549|")
        assertNotNull(segment)
        assertEquals(Mode.TRAM, segment!!.mode)
        assertEquals(2, segment.points.size)
        assertEquals(51.04, segment.points.first().lat, 0.01)
        assertEquals(13.73, segment.points.first().lng, 0.01)
    }

    @Test
    fun `map data without coordinates is dropped`() {
        assertNull(MapDataParser.parse("Footpath|"))
    }
}
