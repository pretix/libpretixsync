package eu.pretix.libpretixsync.check

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import eu.pretix.libpretixsync.DummySentryImplementation
import eu.pretix.libpretixsync.SentryInterface
import eu.pretix.libpretixsync.api.ApiException
import eu.pretix.libpretixsync.api.HttpClientFactory
import eu.pretix.libpretixsync.config.ConfigStore
import eu.pretix.libpretixsync.db.Answer
import eu.pretix.libpretixsync.serialization.JSONArrayDeserializer
import eu.pretix.libpretixsync.serialization.JSONArraySerializer
import eu.pretix.libpretixsync.serialization.JSONObjectDeserializer
import eu.pretix.libpretixsync.serialization.JSONObjectSerializer
import eu.pretix.libpretixsync.utils.NetUtils
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import javax.net.ssl.SSLException

class ProxyCheckProvider(private val config: ConfigStore, httpClientFactory: HttpClientFactory) : TicketCheckProvider {
    private var sentry: SentryInterface = DummySentryImplementation()
    private val client: OkHttpClient
    private val mapper: ObjectMapper

    init {
        client = httpClientFactory.buildClient(NetUtils.ignoreSSLforURL(config.apiUrl))
        mapper = ObjectMapper()
        val m = SimpleModule()
        m.addDeserializer(JSONObject::class.java, JSONObjectDeserializer())
        m.addDeserializer(JSONArray::class.java, JSONArrayDeserializer())
        m.addSerializer(JSONObject::class.java, JSONObjectSerializer())
        m.addSerializer(JSONArray::class.java, JSONArraySerializer())
        mapper.registerModule(m)
        mapper.registerModule(KotlinModule())
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    override fun setSentry(sentry: SentryInterface) {
        this.sentry = sentry
    }

    @Throws(ApiException::class, CheckException::class)
    private fun execute(r: Request): String {
        val response: Response
        response = try {
            client.newCall(r).execute()
        } catch (e: SSLException) {
            e.printStackTrace()
            throw ApiException("Error while creating a secure connection.", e)
        } catch (e: IOException) {
            e.printStackTrace()
            throw ApiException("Connection error: " + e.message, e)
        }
        sentry.addHttpBreadcrumb(r.url.toString(), r.method, response.code)
        var body = ""
        body = try {
            response.body!!.string()
        } catch (e: IOException) {
            e.printStackTrace()
            throw ApiException("Connection error: " + e.message, e)
        }
        if (response.code >= 500) {
            response.close()
            throw ApiException("Server error: " + response.code)
        } else if (response.code == 404) {
            response.close()
            throw ApiException("Server error: Resource not found.")
        } else if (response.code == 403) {
            response.close()
            throw ApiException("Server error: Permission denied.")
        } else if (response.code >= 400) {
            response.close()
            try {
                throw CheckException(JSONObject("body").optString("title", "?"))
            } catch (e: JSONException) {
                throw ApiException(body)
            }
        } else if (response.code >= 405) {
            response.close()
            throw ApiException("Server error: " + response.code + ".")
        }
        return body
    }

    override fun check(eventsAndCheckinLists: Map<String, Long>, ticketid: String, answers: List<Answer>?, ignore_unpaid: Boolean, with_badge_data: Boolean, type: TicketCheckProvider.CheckInType): TicketCheckProvider.CheckResult {
        val data: MutableMap<String, Any> = HashMap()
        data["events_and_checkin_lists"] = eventsAndCheckinLists
        data["ticketid"] = ticketid
        data["answers"] = answers ?: emptyList<Answer>()
        data["ignore_unpaid"] = ignore_unpaid
        data["with_badge_data"] = with_badge_data
        data["type"] = type
        return try {
            val request = Request.Builder()
                    .url(config.apiUrl + "/proxyapi/v1/rpc/check/")  // todo: does not yet exist
                    .post(mapper.writeValueAsString(data).toRequestBody("application/json".toMediaType()))
                    .header("Authorization", "Device " + config.apiKey)
                    .build()
            val body = execute(request)
            mapper.readValue(body, TicketCheckProvider.CheckResult::class.java)
        } catch (e: ApiException) {
            sentry.addBreadcrumb("provider.search", "API Error: " + e.message)
            TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.ERROR, e.message)
        } catch (e: JsonProcessingException) {
            e.printStackTrace()
            TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.ERROR, e.message)
        } catch (e: IOException) {
            e.printStackTrace()
            TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.ERROR, e.message)
        } catch (e: CheckException) {
            TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.ERROR, e.message)
        }
    }

    override fun check(eventsAndCheckinLists: Map<String, Long>, ticketid: String): TicketCheckProvider.CheckResult {
        return check(eventsAndCheckinLists, ticketid, ArrayList(), false, true, TicketCheckProvider.CheckInType.ENTRY)
    }

    @Throws(CheckException::class)
    override fun search(eventsAndCheckinLists: Map<String, Long>, query: String, page: Int): List<TicketCheckProvider.SearchResult> {
        val data: MutableMap<String, Any> = HashMap()
        data["events_and_checkin_lists"] = eventsAndCheckinLists
        data["query"] = query
        data["page"] = page
        return try {
            val request = Request.Builder()
                    .url(config.apiUrl + "/proxyapi/v1/rpc/search/")
                    .post(mapper.writeValueAsString(data).toRequestBody("application/json".toMediaType()))
                    .header("Authorization", "Device " + config.apiKey)
                    .build()
            val body = execute(request)
            mapper.readValue(body)
        } catch (e: ApiException) {
            sentry.addBreadcrumb("provider.search", "API Error: " + e.message)
            throw CheckException(e.message, e)
        } catch (e: JsonProcessingException) {
            e.printStackTrace()
            throw CheckException(e.message, e)
        } catch (e: IOException) {
            e.printStackTrace()
            throw CheckException(e.message, e)
        }
    }

    @Throws(CheckException::class)
    override fun status(eventSlug: String, listId: Long): TicketCheckProvider.StatusResult? {
        val request = Request.Builder()
                .url(config.apiUrl + "/proxyapi/v1/rpc/" + eventSlug + "/" + listId + "/status/")
                .header("Authorization", "Device " + config.apiKey)
                .build()
        return try {
            val body = execute(request)
            mapper.readValue(body, TicketCheckProvider.StatusResult::class.java)
        } catch (e: ApiException) {
            sentry.addBreadcrumb("provider.status", "API Error: " + e.message)
            throw CheckException(e.message, e)
        } catch (e: JsonParseException) {
            e.printStackTrace()
            throw CheckException(e.message, e)
        } catch (e: JsonMappingException) {
            e.printStackTrace()
            throw CheckException(e.message, e)
        } catch (e: IOException) {
            e.printStackTrace()
            throw CheckException(e.message, e)
        }
    }
}