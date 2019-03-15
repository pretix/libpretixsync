package eu.pretix.libpretixsync.setup

import eu.pretix.libpretixsync.api.HttpClientFactory
import eu.pretix.libpretixsync.utils.flatJsonError
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject


open class SetupException(override var message: String?) : Exception(message)
class SetupServerErrorException(override var message: String?) : SetupException(message)
class SetupBadRequestException(override var message: String?) : SetupException(message)
class SetupBadResponseException(override var message: String?) : SetupException(message)

data class SetupResult(val url: String, val api_token: String, val organizer: String, val device_id: Long, val unique_serial: String)

class SetupManager(private val hardware_brand: String, private val hardware_model: String, private val software_brand: String, private val software_version: String, private val http_factory: HttpClientFactory) {
    fun initialize(url: String, token: String): SetupResult {
        val client = http_factory.buildClient()
        val apiBody = JSONObject()
        apiBody.put("token", token)
        apiBody.put("hardware_brand", hardware_brand)
        apiBody.put("hardware_model", hardware_model)
        apiBody.put("software_brand", software_brand)
        apiBody.put("software_version", software_version)

        val request = Request.Builder()
                .url(url + "/api/v1/device/initialize")
                .post(RequestBody.create(MediaType.parse("application/json"), apiBody.toString()))
                .build()
        var response: Response?
        response = client.newCall(request).execute();

        if (response.code() >= 500) {
            response.close()
            throw SetupServerErrorException(response.body()?.string())
        } else if (response.code() == 400) {
            response.close()
            try {
                throw SetupBadRequestException(flatJsonError(JSONObject(response?.body()?.string())))
            } catch (e: JSONException) {
                throw SetupBadResponseException(response?.body()?.string())
            }
        } else {
            try {
                val respo = JSONObject(response?.body()?.string())
                return SetupResult(
                        url, respo.getString("api_token"), respo.getString("organizer"), respo.getLong("device_id"), respo.getString("unique_serial")
                )
            } catch (e: JSONException) {
                e.printStackTrace()
                throw SetupBadResponseException(response?.body()?.string())
            }
        }
    }
}