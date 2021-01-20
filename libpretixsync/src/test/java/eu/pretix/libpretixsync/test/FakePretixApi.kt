package eu.pretix.libpretixsync.test

import eu.pretix.libpretixsync.api.DefaultHttpClientFactory
import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.db.Answer
import okhttp3.MediaType
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.*
import kotlin.collections.ArrayList

class FakePretixApi : PretixApi("http://1.1.1.1/", "a", "demo", "demo", 1, DefaultHttpClientFactory()) {
    val redeemResponses: MutableList<(() -> ApiResponse)> = ArrayList()
    val statusResponses: MutableList<(() -> ApiResponse)> = ArrayList()
    val searchResponses: MutableList<(() -> ApiResponse)> = ArrayList()
    val deleteResponses: MutableList<(() -> ApiResponse)> = ArrayList()
    val postResponses: MutableList<(() -> ApiResponse)> = ArrayList()
    val fetchResponses: MutableList<(() -> ApiResponse)> = ArrayList()
    var downloadResponses: MutableList<(() -> ApiResponse)> = ArrayList()
    var redeemRequestSecret: String? = null
    var redeemRequestDatetime: Date? = null
    var redeemRequestForce = false
    var redeemRequestNonce: String? = null
    var redeemRequestAnswers: List<Answer>? = null
    var redeemRequestListId: Long? = null
    var redeemRequestIgnoreUnpaid = false
    var redeemRequestPdfData = false
    var statusRequestListId: Long? = null
    var searchRequestListId: Long? = null
    var searchRequestQuery: String? = null
    var lastRequestUrl: String? = null
    var lastRequestBody: JSONObject? = null

    override fun redeem(secret: String?, datetime: Date?, force: Boolean, nonce: String?, answers: MutableList<Answer>?, listId: Long?, ignore_unpaid: Boolean, pdf_data: Boolean, type: String?): ApiResponse {
        redeemRequestSecret = secret
        redeemRequestDatetime = datetime
        redeemRequestForce = force
        redeemRequestNonce = nonce
        redeemRequestAnswers = answers
        redeemRequestListId = listId
        redeemRequestIgnoreUnpaid = ignore_unpaid
        redeemRequestPdfData = pdf_data
        return redeemResponses.removeAt(0)()
    }

    override fun status(listId: Long?): ApiResponse {
        statusRequestListId = listId
        return statusResponses.removeAt(0)()
    }

    override fun search(listId: Long?, query: String?, page: Int): ApiResponse {
        searchRequestListId = listId
        searchRequestQuery = query
        return searchResponses.removeAt(0)()
    }

    override fun deleteResource(full_url: String?): ApiResponse {
        lastRequestUrl = full_url
        lastRequestBody = null
        return deleteResponses.removeAt(0)()
    }

    override fun postResource(full_url: String?, data: JSONObject?): ApiResponse {
        lastRequestUrl = full_url
        lastRequestBody = data
        return postResponses.removeAt(0)()
    }

    override fun fetchResource(full_url: String?, if_modified_since: String?): ApiResponse {
        lastRequestUrl = full_url
        lastRequestBody = null
        return fetchResponses.removeAt(0)()
    }

    override fun fetchResource(full_url: String?): ApiResponse {
        lastRequestUrl = full_url
        lastRequestBody = null
        return fetchResponses.removeAt(0)()
    }

    override fun downloadFile(full_url: String?): ApiResponse {
        lastRequestUrl = full_url
        lastRequestBody = null
        return downloadResponses.removeAt(0)()
    }

    override fun uploadFile(file: File?, mediaType: MediaType?, filename: String?): String {
        return "file:abcd"
    }
}
