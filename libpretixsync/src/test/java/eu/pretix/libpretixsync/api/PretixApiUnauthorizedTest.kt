package eu.pretix.libpretixsync.api

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PretixApiUnauthorizedTest {

    private class CannedBody(content: String) : ResponseBody() {
        private val buffer = Buffer().writeUtf8(content)
        var closed = false

        override fun contentType() = "application/json".toMediaType()

        override fun contentLength() = -1L

        override fun source(): BufferedSource = buffer

        override fun close() {
            closed = true
            super.close()
        }
    }

    private fun apiResponding(code: Int, body: CannedBody): PretixApi {
        val factory = object : HttpClientFactory {
            override fun buildClient(ignore_ssl: Boolean): OkHttpClient =
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(code)
                            .message("Canned")
                            .body(body)
                            .build()
                    }
                    .build()
        }
        return PretixApi("https://pretix.example/", "token", "demo", PretixApi.SUPPORTED_API_VERSION, factory)
    }

    @Test
    fun invalidTokenThrowsUnauthorizedWithServerDetail() {
        val body = CannedBody("{\"detail\":\"Invalid token.\"}")
        val api = apiResponding(401, body)

        val e = assertThrows(UnauthorizedApiException::class.java) { api.fetchResource(api.apiURL("device/info")) }

        assertEquals("Invalid token.", e.message)
        assertTrue(body.closed)
    }

    @Test
    fun revokedDeviceThrowsDeviceAccessRevoked() {
        val body = CannedBody("{\"detail\":\"Device access has been revoked.\"}")
        val api = apiResponding(401, body)

        val e = assertThrows(DeviceAccessRevokedException::class.java) { api.fetchResource(api.apiURL("device/info")) }

        assertEquals("Device access has been revoked.", e.message)
        assertTrue(body.closed)
    }

    @Test
    fun unauthorizedWithoutJsonDetailThrowsGenericMessage() {
        val body = CannedBody("<html>Unauthorized</html>")
        val api = apiResponding(401, body)

        val e = assertThrows(UnauthorizedApiException::class.java) { api.fetchResource(api.apiURL("device/info")) }

        assertEquals("Server error: Unauthorized.", e.message)
        assertTrue(body.closed)
    }

    @Test
    fun unauthorizedDownloadClosesUnreadResponse() {
        val body = CannedBody("{\"detail\":\"Invalid token.\"}")
        val api = apiResponding(401, body)

        val e = assertThrows(UnauthorizedApiException::class.java) { api.downloadFile(api.apiURL("device/info")) }

        assertEquals("Server error: Unauthorized.", e.message)
        assertTrue(body.closed)
    }

    @Test
    fun successfulResponseReturnsParsedJson() {
        val api = apiResponding(200, CannedBody("{\"name\":\"Scanner\"}"))

        val result = api.fetchResource(api.apiURL("device/info"))

        assertEquals(200, result.response.code)
        assertEquals("Scanner", result.data!!.getString("name"))
    }
}
