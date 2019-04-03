package eu.pretix.libpretixsync.test

import eu.pretix.libpretixsync.api.DefaultHttpClientFactory
import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.check.TicketCheckProvider
import org.json.JSONObject
import java.util.*

class FakePretixApi : PretixApi("http://1.1.1.1/", "a", "demo", "demo", 1, DefaultHttpClientFactory()) {
    public var redeemResponse: ApiResponse? = null
    public var statusResponse: ApiResponse? = null
    public var searchResponse: ApiResponse? = null
    public var deleteResponse: ApiResponse? = null
    public var postResponse: ApiResponse? = null
    public var fetchResponse: ApiResponse? = null
    public var downloadResponse: ApiResponse? = null
    public var redeemRequestSecret: String? = null
    public var redeemRequestDatetime: Date? = null
    public var redeemRequestForce = false
    public var redeemRequestNonce: String? = null
    public var redeemRequestAnswers: List<TicketCheckProvider.Answer>? = null
    public var redeemRequestListId: Long? = null
    public var redeemRequestIgnoreUnpaid = false
    public var redeemRequestPdfData = false
    public var statusRequestListId: Long? = null
    public var searchRequestListId: Long? = null
    public var searchRequestQuery: String? = null
    public var lastRequestUrl: String? = null
    public var lastRequestBody: JSONObject? = null

    override fun redeem(secret: String?, datetime: Date?, force: Boolean, nonce: String?, answers: MutableList<TicketCheckProvider.Answer>?, listId: Long?, ignore_unpaid: Boolean, pdf_data: Boolean): ApiResponse {
        redeemRequestSecret = secret
        redeemRequestDatetime = datetime
        redeemRequestForce = force
        redeemRequestNonce = nonce
        redeemRequestAnswers = answers
        redeemRequestListId = listId
        redeemRequestIgnoreUnpaid = ignore_unpaid
        redeemRequestPdfData = pdf_data
        return redeemResponse!!
    }

    override fun status(listId: Long?): ApiResponse {
        statusRequestListId = listId
        return statusResponse!!
    }

    override fun search(listId: Long?, query: String?, page: Int): ApiResponse {
        searchRequestListId = listId
        searchRequestQuery = query
        return searchResponse!!
    }

    override fun deleteResource(full_url: String?): ApiResponse {
        lastRequestUrl = full_url
        lastRequestBody = null
        return deleteResponse!!
    }

    override fun postResource(full_url: String?, data: JSONObject?): ApiResponse {
        lastRequestUrl = full_url
        lastRequestBody = data
        return postResponse!!
    }

    override fun fetchResource(full_url: String?, if_modified_since: String?): ApiResponse {
        lastRequestUrl = full_url
        lastRequestBody = null
        return fetchResponse!!
    }

    override fun fetchResource(full_url: String?): ApiResponse {
        lastRequestUrl = full_url
        lastRequestBody = null
        return fetchResponse!!
    }

    override fun downloadFile(full_url: String?): ApiResponse {
        lastRequestUrl = full_url
        lastRequestBody = null
        return downloadResponse!!
    }
}
