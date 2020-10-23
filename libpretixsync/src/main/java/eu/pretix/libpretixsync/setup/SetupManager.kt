package eu.pretix.libpretixsync.setup

import eu.pretix.libpretixsync.api.HttpClientFactory
import eu.pretix.libpretixsync.utils.NetUtils
import eu.pretix.libpretixsync.utils.flatJsonError
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject


open class SetupException(override var message: String?) : Exception(message)
class SetupServerErrorException(override var message: String?) : SetupException(message)
class SetupBadRequestException(override var message: String?) : SetupException(message)
class SetupBadResponseException(override var message: String?) : SetupException(message)

data class SetupResult(val url: String, val api_token: String, val organizer: String, val device_id: Long, val unique_serial: String, val security_profile: String, val device_name: String, val gate_name: String?)

class SetupManager(private val hardware_brand: String, private val hardware_model: String, private val software_brand: String, private val software_version: String, private val http_factory: HttpClientFactory) {
    fun initialize(url: String, token: String): SetupResult {
        val client = http_factory.buildClient(NetUtils.ignoreSSLforURL(url));
        val apiBody = JSONObject()
        apiBody.put("token", token)
        apiBody.put("hardware_brand", hardware_brand)
        apiBody.put("hardware_model", hardware_model)
        apiBody.put("software_brand", software_brand)
        apiBody.put("software_version", software_version)

        val request = Request.Builder()
                .url(url + "/api/v1/device/initialize")
                .post(apiBody.toString().toByteArray().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()
        var response: Response?
        response = client.newCall(request).execute();

        val body = response.body?.string()
        val code = response.code
        response.close()

        if (code >= 500) {
            throw SetupServerErrorException(body)
        } else if (code == 400) {
            try {
                throw SetupBadRequestException(flatJsonError(JSONObject(body)))
            } catch (e: JSONException) {
                throw SetupBadResponseException(body)
            }
        } else {
            try {
                val respo = JSONObject(body)
                val gate = if (respo.has("gate") && !respo.isNull("gate")) respo.getJSONObject("gate").getString("name") else null
                return SetupResult(
                        url, respo.getString("api_token"), respo.getString("organizer"), respo.getLong("device_id"), respo.getString("unique_serial"),
                        respo.optString("security_profile", "full"), respo.optString("name"), gate
                )
            } catch (e: JSONException) {
                e.printStackTrace()
                throw SetupBadResponseException(body)
            }
        }
    }
}