package eu.pretix.libpretixsync.api

import eu.pretix.libpretixsync.DummySentryImplementation
import eu.pretix.libpretixsync.SentryInterface
import eu.pretix.libpretixsync.config.ConfigStore
import eu.pretix.libpretixsync.db.Answer
import eu.pretix.libpretixsync.db.Question
import eu.pretix.libpretixsync.db.QueuedCheckIn
import eu.pretix.libpretixsync.db.ReceiptLine
import eu.pretix.libpretixsync.utils.NetUtils
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.net.MalformedURLException
import java.net.URL
import java.net.URLEncoder
import java.util.*
import javax.net.ssl.SSLException
import javax.net.ssl.SSLPeerUnverifiedException

open class PretixApi(url: String, key: String, orgaSlug: String, eventSlug: String?, version: Int, httpClientFactory: HttpClientFactory) {
    private val url: String
    public var eventSlug: String?
    private val orgaSlug: String
    private val key: String
    private val version: Int
    private val client: OkHttpClient
    var sentry: SentryInterface

    inner class ApiResponse(val data: JSONObject?, val response: Response)

    @Throws(ApiException::class, JSONException::class)
    open fun redeem(secret: String, datetime: Date?, force: Boolean, nonce: String?, answers: List<Answer>?, listId: Long, ignore_unpaid: Boolean, pdf_data: Boolean, type: String?): ApiResponse {
        var dt: String? = null
        if (datetime != null) {
            dt = QueuedCheckIn.formatDatetime(datetime)
        }
        return redeem(secret, dt, force, nonce, answers, listId, ignore_unpaid, pdf_data, type)
    }

    @Throws(ApiException::class, JSONException::class)
    fun redeem(secret: String, datetime: String?, force: Boolean, nonce: String?, answers: List<Answer>?, listId: Long, ignore_unpaid: Boolean, pdf_data: Boolean, type: String?): ApiResponse {
        val body = JSONObject()
        if (datetime != null) {
            body.put("datetime", datetime)
        }
        body.put("force", force)
        body.put("ignore_unpaid", ignore_unpaid)
        body.put("nonce", nonce)
        body.put("type", type)
        val answerbody = JSONObject()
        if (answers != null) {
            for (a in answers) {
                if (a.value.startsWith("file:///")) {
                    val fileid = uploadFile(File(a.value.substring(7)), when (a.value.split(".").last()) {
                        "jpeg", "jpg" -> "image/jpeg".toMediaTypeOrNull()!!
                        "png" -> "image/png".toMediaTypeOrNull()!!
                        "gif" -> "image/gif".toMediaTypeOrNull()!!
                        "pdf" -> "application/pdf".toMediaTypeOrNull()!!
                        else -> "application/unknown".toMediaTypeOrNull()!!
                    }, a.value.split("/").last())
                    answerbody.put("" + (a.question as Question).getServer_id(), fileid)
                } else {
                    answerbody.put("" + (a.question as Question).getServer_id(), a.value)
                }
            }
        }
        body.put("answers", answerbody)
        body.put("questions_supported", true)
        body.put("canceled_supported", true)
        var pd = ""
        if (pdf_data) {
            pd = "?pdf_data=true"
        }
        return postResource(eventResourceUrl("checkinlists/" + listId + "/positions/" + URLEncoder.encode(secret) + "/redeem") + pd, body)
    }

    @Throws(ApiException::class)
    open fun status(listId: Long): ApiResponse {
        return try {
            fetchResource(eventResourceUrl("checkinlists/$listId/status")!!)
        } catch (resourceNotModified: ResourceNotModified) {
            throw FinalApiException("invalid error")
        }
    }

    @Throws(ApiException::class)
    open fun search(listId: Long, query: String?, page: Int): ApiResponse {
        return try {
            fetchResource(eventResourceUrl("checkinlists/$listId/positions") + "?ignore_status=true&page=" + page + "&search=" + URLEncoder.encode(query, "UTF-8"))
        } catch (resourceNotModified: ResourceNotModified) {
            throw FinalApiException("invalid error")
        } catch (resourceNotModified: UnsupportedEncodingException) {
            throw FinalApiException("invalid error")
        }
    }

    fun apiURL(suffix: String): String {
        return try {
            URL(URL(url), "/api/v1/$suffix").toString()
        } catch (e: MalformedURLException) {
            e.printStackTrace()
            ""
        }
    }

    fun organizerResourceUrl(resource: String): String {
        return try {
            URL(URL(url), "/api/v1/organizers/$orgaSlug/$resource/").toString()
        } catch (e: MalformedURLException) {
            e.printStackTrace()
            ""
        }
    }

    fun eventResourceUrl(resource: String): String {
        return try {
            URL(URL(url), "/api/v1/organizers/$orgaSlug/events/${eventSlug!!}/$resource/").toString()
        } catch (e: MalformedURLException) {
            e.printStackTrace()
            ""
        }
    }

    @Throws(ApiException::class)
    open fun deleteResource(full_url: String): ApiResponse {
        val request = Request.Builder()
                .url(full_url)
                .delete()
                .header("Authorization", "Device $key")
        return try {
            apiCall(request.build(), false)
        } catch (resourceNotModified: ResourceNotModified) {
            resourceNotModified.printStackTrace()
            throw FinalApiException("Resource not modified")
        }
    }

    @Throws(ApiException::class)
    open fun patchResource(full_url: String, data: JSONObject): ApiResponse {
        return patchResource(full_url, data, null)
    }

    @Throws(ApiException::class)
    fun patchResource(full_url: String?, data: JSONObject, idempotency_key: String?): ApiResponse {
        var request = Request.Builder()
                .url(full_url!!)
                .patch(data.toString().toRequestBody("application/json".toMediaType()))
                .header("Authorization", "Device $key")
        if (idempotency_key != null) {
            request = request.header("X-Idempotency-Key", idempotency_key)
        }
        return try {
            apiCall(request.build())
        } catch (resourceNotModified: ResourceNotModified) {
            resourceNotModified.printStackTrace()
            throw FinalApiException("Resource not modified")
        }
    }

    @Throws(ApiException::class)
    open fun postResource(full_url: String, data: JSONObject): ApiResponse {
        return postResource(full_url, data.toString(), null)
    }

    @Throws(ApiException::class)
    open fun postResource(full_url: String, data: JSONArray): ApiResponse {
        return postResource(full_url, data.toString(), null)
    }

    @Throws(ApiException::class)
    fun postResource(full_url: String?, data: JSONObject, idempotency_key: String?): ApiResponse {
        return postResource(full_url, data.toString(), idempotency_key)
    }

    @Throws(ApiException::class)
    fun postResource(full_url: String?, data: String, idempotency_key: String?): ApiResponse {
        var request = Request.Builder()
                .url(full_url!!)
                .post(data.toRequestBody("application/json".toMediaType()))
                .header("Authorization", "Device $key")
        if (idempotency_key != null) {
            request = request.header("X-Idempotency-Key", idempotency_key)
        }
        return try {
            apiCall(request.build())
        } catch (resourceNotModified: ResourceNotModified) {
            resourceNotModified.printStackTrace()
            throw FinalApiException("Resource not modified")
        }
    }

    @Throws(ApiException::class, ResourceNotModified::class)
    open fun fetchResource(full_url: String, if_modified_since: String? = null): ApiResponse {
        var request = Request.Builder()
                .url(full_url)
                .header("Authorization", "Device $key")
        if (if_modified_since != null) {
            request = request.header("If-Modified-Since", if_modified_since)
        }
        return apiCall(request.get().build())
    }

    @Throws(ApiException::class, ResourceNotModified::class)
    open fun fetchResource(full_url: String): ApiResponse {
        return fetchResource(full_url, null)
    }

    @Throws(ApiException::class)
    open fun downloadFile(full_url: String): ApiResponse? {
        val request = Request.Builder()
                .url(full_url)
                .header("Authorization", "Device $key")
        return try {
            apiCall(request.build(), false)
        } catch (resourceNotModified: ResourceNotModified) {
            resourceNotModified.printStackTrace()
            throw FinalApiException("Resource not modified")
        }
    }

    @Throws(ApiException::class, ResourceNotModified::class)
    private fun apiCall(request: Request, json: Boolean = true, is_retry: Boolean=false): ApiResponse {
        val response: Response
        response = try {
            client.newCall(request).execute()
        } catch (e: SSLPeerUnverifiedException) {
            if (!is_retry) {
                // On Windows, we occasionally see SSL errors after a long period of idle. We didn't fully figure
                // it out, but it seems to be because it tries to reuse a SSL socket that the server already closed.
                // We assume it's safe to retry in all cases since the verification happens before any payload
                // reaches the server.
                client.connectionPool.evictAll()
                return apiCall(request, json, is_retry)
            }
            e.printStackTrace()
            throw ApiException("Error while creating a secure connection.", e)
        } catch (e: IOException) {
            e.printStackTrace()
            throw ApiException("Connection error: " + e.message, e)
        }
        val safe_url = request.url.toString().replace("^(.*)key=([0-9A-Za-z]+)([^0-9A-Za-z]*)".toRegex(), "$1key=redacted$3")
        sentry.addHttpBreadcrumb(safe_url, request.method, response.code)
        var body = ""
        if (json) {
            body = try {
                response.body!!.string()
            } catch (e: IOException) {
                e.printStackTrace()
                throw ApiException("Connection error: " + e.message, e)
            }
        }
        if (response.code >= 500) {
            response.close()
            throw ApiException("Server error: " + response.code)
        } else if (response.code == 404 && (!json || !body.startsWith("{"))) {
            response.close()
            throw NotFoundApiException("Server error: Resource not found.")
        } else if (response.code == 304) {
            throw ResourceNotModified()
        } else if (response.code == 403) {
            response.close()
            throw FinalApiException("Server error: Permission denied.")
        } else if (response.code == 409) {
            response.close()
            throw ConflictApiException("Server error: " + response.code + ": " + body)
        } else if (response.code >= 405) {
            response.close()
            throw FinalApiException("Server error: " + response.code + ".")
        }
        if (response.code == 401) {
            if (body.startsWith("{")) {
                try {
                    val err = JSONObject(body)
                    if (err.optString("detail", "") == "Device access has been revoked.") {
                        throw DeviceAccessRevokedException("Device access has been revoked.")
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
        }
        return try {
            if (json) {
                if (body.startsWith("[")) {
                    body = "{\"content\": $body}"
                }
                ApiResponse(
                        JSONObject(body),
                        response
                )
            } else {
                ApiResponse(
                        null,
                        response
                )
            }
        } catch (e: JSONException) {
            e.printStackTrace()
            sentry.captureException(e)
            throw ApiException("Invalid JSON received: " + body.substring(0, 100), e)
        }
    }

    @Throws(ApiException::class)
    open fun uploadFile(file: File, mediaType: MediaType, filename: String): String {
        val request = Request.Builder()
                .url(apiURL("upload")!!)
                .post(file.asRequestBody(mediaType))
                .header("Content-Disposition", "attachment; filename=\"$filename\"")
                .header("Authorization", "Device $key")
        return try {
            val resp = apiCall(request.build())
            if (resp.response.code != 201) {
                throw FinalApiException("Could not upload file: " + resp.data)
            }
            resp.data!!.getString("id")
        } catch (exc: ResourceNotModified) { // can't happen
            throw FinalApiException("resource not modified")
        } catch (exc: JSONException) {
            throw FinalApiException("JSONException")
        }
    }

    companion object {
        /**
         * See https://docs.pretix.eu/en/latest/api/index.html for API documentation
         */
        const val SUPPORTED_API_VERSION = 4
        val JSON: MediaType = "application/json; charset=utf-8".toMediaType()
        fun fromConfig(config: ConfigStore, httpClientFactory: HttpClientFactory?=null): PretixApi {
            return PretixApi(config.apiUrl, config.apiKey, config.organizerSlug,
                    config.eventSlug, config.apiVersion, httpClientFactory ?: DefaultHttpClientFactory())
        }

        fun fromConfig(config: ConfigStore): PretixApi {
            return fromConfig(config, null)
        }
    }

    init {
        var url = url
        if (!url.endsWith("/")) {
            url += "/"
        }
        this.url = url
        this.key = key
        this.eventSlug = eventSlug
        this.orgaSlug = orgaSlug
        this.version = version
        client = httpClientFactory.buildClient(NetUtils.ignoreSSLforURL(url))
        sentry = DummySentryImplementation()
    }
}