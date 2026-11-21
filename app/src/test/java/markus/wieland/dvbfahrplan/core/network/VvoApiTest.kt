package markus.wieland.dvbfahrplan.core.network

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class VvoApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: VvoApi

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        api = VvoApi(OkHttpClient(), server.url("/"))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `requests are JSON posts`() = runTest {
        server.enqueue(MockResponse().setBody("""{"Status":{"Code":"Ok"},"Points":[]}"""))
        api.findStops("Albertplatz")
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/tr/pointfinder", request.path)
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))
        assertEquals("application/json", request.getHeader("Accept"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"query\":\"Albertplatz\""))
        assertTrue("format must always be sent", body.contains("\"format\":\"json\""))
    }

    @Test
    fun `service errors inside HTTP 200 are raised`() = runTest {
        server.enqueue(MockResponse().setBody("""{"Status":{"Code":"ServiceError","Message":"stop invalid"}}"""))
        try {
            api.departures(DepartureRequest(stopid = "nope"))
            fail("expected an exception")
        } catch (e: VvoException.Service) {
            assertEquals("ServiceError", e.code)
        }
    }

    @Test
    fun `http errors are raised`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody("{}"))
        try {
            api.lines("x")
            fail("expected an exception")
        } catch (e: VvoException.Http) {
            assertEquals(400, e.code)
        }
    }
}
