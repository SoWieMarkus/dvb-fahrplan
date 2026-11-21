package markus.wieland.dvbfahrplan.core.network

import markus.wieland.dvbfahrplan.core.model.Mode
import markus.wieland.dvbfahrplan.core.model.RealtimeState
import markus.wieland.dvbfahrplan.core.model.TimePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Runs the mappers against real responses captured from webapi.vvo-online.de (see test/resources/fixtures). */
class MappersTest {

    private val json = VvoApi.DefaultJson

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader!!.getResource("fixtures/$name")) { "missing fixture $name" }.readText()

    @Test
    fun `dates are epoch millis regardless of offset suffix`() {
        assertEquals(Instant.ofEpochMilli(1795276140000), VvoDate.parse("/Date(1795276140000-0000)/"))
        assertEquals(Instant.ofEpochMilli(1795276140000), VvoDate.parse("\\/Date(1795276140000+0100)\\/"))
        assertNull(VvoDate.parse(null))
        assertNull(VvoDate.parse("garbage"))
    }

    @Test
    fun `delay is rounded to whole minutes in both directions`() {
        val scheduled = Instant.parse("2026-11-21T12:00:00Z")
        assertEquals(4, TimePoint(scheduled, scheduled.plusSeconds(240)).delayMinutes)
        assertEquals(-1, TimePoint(scheduled, scheduled.minusSeconds(60)).delayMinutes)
        assertEquals(0, TimePoint(scheduled, scheduled).delayMinutes)
        assertNull(TimePoint(scheduled, null).delayMinutes)
    }

    @Test
    fun `point finder strings become stops`() {
        val stops = json.decodeFromString<PointFinderResponse>(fixture("pointfinder.json")).toStops()
        assertEquals("33000013", stops.first().id)
        assertEquals("Albertplatz", stops.first().name)
        assertEquals("Dresden", stops.first().displayPlace)
        assertEquals("Cossebaude/Neu-Leuteritz", stops[1].place)
        assertNotNull(stops.first().location)
    }

    @Test
    fun `departure monitor is mapped`() {
        val departures = json.decodeFromString<DepartureMonitorResponse>(fixture("departures.json")).toDepartures()
        assertTrue(departures.isNotEmpty())
        val first = departures.first()
        assertEquals("3", first.line)
        assertEquals(Mode.TRAM, first.mode)
        assertEquals("4", first.platform?.name)
        val sBahn = departures.first { it.line == "S2" }
        assertEquals(Mode.SUBURBAN, sBahn.mode)
        assertEquals(RealtimeState.DELAYED, sBahn.time.state)
        assertEquals(4, sBahn.time.delayMinutes)
        assertTrue(sBahn.platform!!.isTrack)
    }

    @Test
    fun `routes are mapped with legs and map data`() {
        val search = json.decodeFromString<RoutesResponse>(fixture("trips_interchange.json")).toRouteSearch()
        assertNotNull(search.sessionId)
        val route = search.routes.first()
        assertTrue(route.legs.size > 1)
        assertTrue(route.legs.any { it.mode == Mode.FOOTPATH })
        assertTrue(route.legs.any { it.mode == Mode.STAIRS })
        assertTrue(route.path.isNotEmpty())
        assertNotNull(route.departure)
        assertNotNull(route.arrival)
        assertTrue(route.arrival!!.scheduled > route.departure!!.scheduled)
    }

    @Test
    fun `prevnext returns earlier plus newly found routes`() {
        val before = json.decodeFromString<RoutesResponse>(fixture("trips.json")).toRouteSearch()
        val after = json.decodeFromString<RoutesResponse>(fixture("prevnext.json")).toRouteSearch()
        assertEquals(before.sessionId, after.sessionId)
        assertTrue(after.routes.size > before.routes.size)
    }

    @Test
    fun `trip contains stops with position and path`() {
        val trip = json.decodeFromString<TripResponse>(fixture("trip.json")).toTrip()
        assertTrue(trip.stops.size > 5)
        assertNotNull(trip.stops.first().location)
        assertTrue(trip.path.isNotEmpty())
    }

    @Test
    fun `route changes resolve their line names`() {
        val disruptions = json.decodeFromString<RouteChangesResponse>(fixture("routechanges.json")).toDisruptions()
        assertTrue(disruptions.isNotEmpty())
        assertTrue(disruptions.any { it.lines.isNotEmpty() })
        assertTrue(disruptions.first().validity.isNotEmpty())
    }

    @Test
    fun `change ids of departures point to route changes`() {
        val departures = json.decodeFromString<DepartureMonitorResponse>(fixture("departures.json")).toDepartures()
        val disruptionIds = json.decodeFromString<RouteChangesResponse>(fixture("routechanges.json")).toDisruptions().map { it.id }.toSet()
        val referenced = departures.flatMap { it.changeIds }.toSet()
        assertTrue(referenced.isNotEmpty())
        assertTrue(disruptionIds.containsAll(referenced))
    }

    @Test
    fun `lines of a stop are mapped`() {
        val lines = json.decodeFromString<LinesResponse>(fixture("lines.json")).toLines()
        assertEquals("3", lines.first().name)
        assertEquals(Mode.TRAM, lines.first().mode)
        assertEquals(2, lines.first().directions.size)
    }
}
