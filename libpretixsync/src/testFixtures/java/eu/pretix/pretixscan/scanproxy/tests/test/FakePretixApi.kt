package eu.pretix.pretixscan.scanproxy.tests.test

import eu.pretix.libpretixsync.api.DefaultHttpClientFactory
import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.db.Answer
import okhttp3.MediaType
import org.json.JSONException
import org.json.JSONObject
import java.io.File

class FakePretixApi : PretixApi("http://1.1.1.1/", "a", "demo", 1, DefaultHttpClientFactory()) {
    val redeemResponses: MutableList<(() -> ApiResponse)> = ArrayList()
    val statusResponses: MutableList<(() -> ApiResponse)> = ArrayList()
    val searchResponses: MutableList<(() -> ApiResponse)> = ArrayList()
    val deleteResponses: MutableList<(() -> ApiResponse)> = ArrayList()
    val postResponses: MutableList<(() -> ApiResponse)> = ArrayList()
    val fetchResponses: MutableList<(() -> ApiResponse)> = ArrayList()
    var downloadResponses: MutableList<(() -> ApiResponse)> = ArrayList()
    var redeemRequestSecret: String? = null
    var redeemRequestDatetime: String? = null
    var redeemRequestForce = false
    var redeemRequestNonce: String? = null
    var redeemRequestAnswers: List<Answer>? = null
    var redeemRequestLists: List<Long>? = null
    var redeemRequestIgnoreUnpaid = false
    var redeemRequestPdfData = false
    var statusRequestListId: Long? = null
    var searchRequestLists: List<Long>? = null
    var searchRequestQuery: String? = null
    var lastRequestUrl: String? = null
    var lastRequestBody: JSONObject? = null

    override fun redeem(
        lists: List<Long>,
        secret: String,
        datetime: String?,
        force: Boolean,
        nonce: String?,
        answers: List<Answer>?,
        ignore_unpaid: Boolean,
        pdf_data: Boolean,
        type: String?,
        source_type: String?,
        callTimeout: Long?,
        questions_supported: Boolean,
        use_order_locale: Boolean
    ): ApiResponse {
        redeemRequestSecret = secret
        redeemRequestDatetime = datetime
        redeemRequestForce = force
        redeemRequestNonce = nonce
        redeemRequestAnswers = answers
        redeemRequestLists = lists
        redeemRequestIgnoreUnpaid = ignore_unpaid
        redeemRequestPdfData = pdf_data
        return redeemResponses.removeAt(0)()
    }

    override fun redeem(
        eventSlug: String,
        secret: String,
        datetime: String?,
        force: Boolean,
        nonce: String?,
        answers: List<Answer>?,
        listId: Long,
        ignore_unpaid: Boolean,
        pdf_data: Boolean,
        type: String?,
        source_type: String?,
        callTimeout: Long?,
        questions_supported: Boolean,
        use_order_locale: Boolean
    ): ApiResponse {
        redeemRequestSecret = secret
        redeemRequestDatetime = datetime
        redeemRequestForce = force
        redeemRequestNonce = nonce
        redeemRequestAnswers = answers
        redeemRequestLists = listOf(listId)
        redeemRequestIgnoreUnpaid = ignore_unpaid
        redeemRequestPdfData = pdf_data
        return redeemResponses.removeAt(0)()
    }

    override fun status(eventSlug: String, listId: Long): ApiResponse {
        statusRequestListId = listId
        return statusResponses.removeAt(0)()
    }

    override fun search(eventSlug: String, listId: Long, query: String?, page: Int): ApiResponse {
        searchRequestLists = listOf(listId)
        searchRequestQuery = query
        return searchResponses.removeAt(0)()
    }

    override fun search(lists: List<Long>, query: String?, page: Int): ApiResponse {
        searchRequestLists = lists
        searchRequestQuery = query
        return searchResponses.removeAt(0)()
    }

    override fun deleteResource(full_url: String, idempotency_key: String?): ApiResponse {
        lastRequestUrl = full_url
        lastRequestBody = null
        return deleteResponses.removeAt(0)()
    }

    override fun postResource(
        full_url: String,
        data: String,
        idempotency_key: String?,
        callTimeout: Long?
    ): ApiResponse {
        lastRequestUrl = full_url
        try {
            lastRequestBody = JSONObject(data)
        } catch (e: JSONException) {
            lastRequestBody = null
        }
        return postResponses.removeAt(0)()
    }

    override fun fetchResource(full_url: String, if_modified_since: String?): ApiResponse {
        lastRequestUrl = full_url
        lastRequestBody = null
        return fetchResponses.removeAt(0)()
    }

    override fun downloadFile(full_url: String): ApiResponse {
        lastRequestUrl = full_url
        lastRequestBody = null
        return downloadResponses.removeAt(0)()
    }

    override fun uploadFile(file: File, mediaType: MediaType, filename: String): String {
        return "file:abcd"
    }


    fun reset() {
        postResponses.clear()
        deleteResponses.clear()
        downloadResponses.clear()
        fetchResponses.clear()
        redeemResponses.clear()
        statusResponses.clear()
        searchResponses.clear()
    }
}
