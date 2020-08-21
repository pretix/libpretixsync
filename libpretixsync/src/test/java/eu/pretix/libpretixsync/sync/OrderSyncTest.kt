package eu.pretix.libpretixsync.sync

import eu.pretix.libpretixsync.api.ApiException
import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.db.*
import eu.pretix.libpretixsync.sync.*
import eu.pretix.libpretixsync.test.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Before
import org.junit.Test

import java.util.ArrayList
import java.util.Date

import org.junit.Assert.assertEquals
import org.junit.Assert.fail

class OrderSyncTest : BaseDatabaseTest() {
    private lateinit var configStore: FakeConfigStore
    private lateinit var fakeApi: FakePretixApi
    private lateinit var osa: OrderSyncAdapter

    @Before
    fun setUpFakes() {
        configStore = FakeConfigStore()
        fakeApi = FakePretixApi()
        osa = OrderSyncAdapter(dataStore, FakeFileStorage(), "demo", true, false, fakeApi, null)

        CheckInListSyncAdapter(dataStore, FakeFileStorage(), "demo", 0L, fakeApi, null).standaloneRefreshFromJSON(jsonResource("checkinlists/list1.json"))
    }

    private fun response(data: JSONObject, generated: String): Response {
        return Response.Builder()
                .request(Request.Builder().url("https://foo").build())
                .code(200)
                .message("OK")
                .protocol(Protocol.HTTP_1_1)
                .addHeader("X-Page-Generated", generated)
                .body(ResponseBody.create("application/json".toMediaTypeOrNull(), data.toString()))
                .build()
    }

    @Test
    fun testSimpleInitialDownload() {
        fakeApi.fetchResponses.add {
            val respdata = JSONObject()
            respdata.put("count", 2)
            respdata.put("next", JSONObject.NULL)
            val results = JSONArray()
            results.put(jsonResource("orders/order1.json"))
            results.put(jsonResource("orders/order2.json"))
            respdata.put("results", results)

            fakeApi.ApiResponse(respdata, response(respdata, "timestamp1"))
        }

        osa.download()
        assertEquals("http://1.1.1.1/api/v1/organizers/demo/events/demo/orders/?testmode=false&exclude=downloads&exclude=payment_date&exclude=payment_provider&exclude=fees&exclude=positions.downloads&exclude=payments&exclude=refunds&pdf_data=true", fakeApi.lastRequestUrl)

        assertEquals(2, dataStore.count(Order::class.java).get().value())
        assertEquals(5, dataStore.count(OrderPosition::class.java).get().value())
        assertEquals(2, dataStore.count(CheckIn::class.java).get().value())
        val rlm = dataStore.select(ResourceLastModified::class.java).where(ResourceLastModified.RESOURCE.eq("orders_withpdfdata")).get().first()
        assertEquals(rlm.getEvent_slug(), "demo")
        assertEquals(rlm.getLast_modified(), "timestamp1")
        assertEquals(rlm.getStatus(), "complete")
    }

    @Test
    fun testProcessInitialByPage() {
        fakeApi.fetchResponses.add {
            val respdata = JSONObject()
            respdata.put("count", 2)
            respdata.put("next", "%page2")
            val results = JSONArray()
            results.put(jsonResource("orders/order1.json"))
            respdata.put("results", results)
            fakeApi.ApiResponse(respdata, response(respdata, "timestamp1"))
        }

        fakeApi.fetchResponses.add {
            val respdata = JSONObject()
            respdata.put("count", 2)
            respdata.put("next", JSONObject.NULL)
            val results = JSONArray()
            results.put(jsonResource("orders/order2.json"))
            respdata.put("results", results)
            fakeApi.ApiResponse(respdata, response(respdata, "timestamp2"))
        }

        osa.download()
        assertEquals("%page2?testmode=false&exclude=downloads&exclude=payment_date&exclude=payment_provider&exclude=fees&exclude=positions.downloads&exclude=payments&exclude=refunds&pdf_data=true", fakeApi.lastRequestUrl)

        assertEquals(2, dataStore.count(Order::class.java).get().value())
        assertEquals(5, dataStore.count(OrderPosition::class.java).get().value())
        assertEquals(2, dataStore.count(CheckIn::class.java).get().value())

        val rlm = dataStore.select(ResourceLastModified::class.java).where(ResourceLastModified.RESOURCE.eq("orders_withpdfdata")).get().first()
        assertEquals(rlm.getEvent_slug(), "demo")
        assertEquals(rlm.getLast_modified(), "timestamp1")
        assertEquals(rlm.getStatus(), "complete")
    }

    @Test
    fun testInitialInterruptedResume() {
        fakeApi.fetchResponses.add {
            val respdata = JSONObject()
            respdata.put("count", 4)
            respdata.put("next", "%page2")
            val results = JSONArray()
            results.put(jsonResource("orders/order1.json"))
            respdata.put("results", results)
            fakeApi.ApiResponse(respdata, response(respdata, "timestamp1"))
        }
        fakeApi.fetchResponses.add {
            val respdata = JSONObject()
            respdata.put("count", 4)
            respdata.put("next", "%page3")
            val results = JSONArray()
            results.put(jsonResource("orders/order2.json"))
            respdata.put("results", results)
            fakeApi.ApiResponse(respdata, response(respdata, "timestamp2"))
        }

        fakeApi.fetchResponses.add { throw ApiException("Unreachable!") }

        try {
            osa.download()
            fail("Expected ApiException to be thrown")
        } catch (e: ApiException) {
        }

        assertEquals("%page3?testmode=false&exclude=downloads&exclude=payment_date&exclude=payment_provider&exclude=fees&exclude=positions.downloads&exclude=payments&exclude=refunds&pdf_data=true", fakeApi.lastRequestUrl)

        assertEquals(2, dataStore.count(Order::class.java).get().value())
        assertEquals(5, dataStore.count(OrderPosition::class.java).get().value())
        assertEquals(2, dataStore.count(CheckIn::class.java).get().value())

        val rlm = dataStore.select(ResourceLastModified::class.java).where(ResourceLastModified.RESOURCE.eq("orders_withpdfdata")).get().first()
        assertEquals(rlm.getEvent_slug(), "demo")
        assertEquals(rlm.getLast_modified(), "timestamp1")
        assertEquals(rlm.getStatus(), "incomplete:2019-01-01T00:11:30Z")

        fakeApi.fetchResponses.add {
            val respdata = JSONObject()
            respdata.put("count", 3)
            respdata.put("next", JSONObject.NULL)
            val results = JSONArray()
            results.put(jsonResource("orders/order2.json"))
            results.put(jsonResource("orders/order3.json"))
            results.put(jsonResource("orders/order4.json"))
            respdata.put("results", results)
            fakeApi.ApiResponse(respdata, response(respdata, "timestamp3"))
        }

        osa.download()
        assertEquals("http://1.1.1.1/api/v1/organizers/demo/events/demo/orders/?testmode=false&exclude=downloads&exclude=payment_date&exclude=payment_provider&exclude=fees&exclude=positions.downloads&exclude=payments&exclude=refunds&pdf_data=true&ordering=datetime&created_since=2019-01-01T00%3A11%3A30Z", fakeApi.lastRequestUrl)
        assertEquals(4, dataStore.count(Order::class.java).get().value())

        val rlm2 = dataStore.select(ResourceLastModified::class.java).where(ResourceLastModified.RESOURCE.eq("orders_withpdfdata")).get().first()
        assertEquals(rlm2.getEvent_slug(), "demo")
        assertEquals(rlm2.getLast_modified(), "timestamp1")
        assertEquals(rlm2.getStatus(), "complete")
    }

    @Test
    fun testInitialInterruptedMultipleTimes() {
        fakeApi.fetchResponses.add {
            val respdata = JSONObject()
            respdata.put("count", 4)
            respdata.put("next", "%page2")
            val results = JSONArray()
            results.put(jsonResource("orders/order1.json"))
            respdata.put("results", results)
            fakeApi.ApiResponse(respdata, response(respdata, "timestamp1"))
        }
        fakeApi.fetchResponses.add {
            val respdata = JSONObject()
            respdata.put("count", 4)
            respdata.put("next", "%page3")
            val results = JSONArray()
            results.put(jsonResource("orders/order2.json"))
            respdata.put("results", results)
            fakeApi.ApiResponse(respdata, response(respdata, "timestamp2"))
        }

        fakeApi.fetchResponses.add { throw ApiException("Unreachable!") }

        try {
            osa.download()
            fail("Expected ApiException to be thrown")
        } catch (e: ApiException) {
        }

        assertEquals("%page3?testmode=false&exclude=downloads&exclude=payment_date&exclude=payment_provider&exclude=fees&exclude=positions.downloads&exclude=payments&exclude=refunds&pdf_data=true", fakeApi.lastRequestUrl)

        assertEquals(2, dataStore.count(Order::class.java).get().value())
        assertEquals(5, dataStore.count(OrderPosition::class.java).get().value())
        assertEquals(2, dataStore.count(CheckIn::class.java).get().value())

        val rlm = dataStore.select(ResourceLastModified::class.java).where(ResourceLastModified.RESOURCE.eq("orders_withpdfdata")).get().first()
        assertEquals(rlm.getEvent_slug(), "demo")
        assertEquals(rlm.getLast_modified(), "timestamp1")
        assertEquals(rlm.getStatus(), "incomplete:2019-01-01T00:11:30Z")


        fakeApi.fetchResponses.add {
            val respdata = JSONObject()
            respdata.put("count", 3)
            respdata.put("next", "%page4")
            val results = JSONArray()
            results.put(jsonResource("orders/order2.json"))
            results.put(jsonResource("orders/order3.json"))
            respdata.put("results", results)
            fakeApi.ApiResponse(respdata, response(respdata, "timestamp3"))
        }
        fakeApi.fetchResponses.add { throw ApiException("Unreachable!") }

        try {
            osa.download()
            fail("Expected ApiException to be thrown")
        } catch (e: ApiException) {
        }

        assertEquals("%page4?testmode=false&exclude=downloads&exclude=payment_date&exclude=payment_provider&exclude=fees&exclude=positions.downloads&exclude=payments&exclude=refunds&pdf_data=true&ordering=datetime&created_since=2019-01-01T00%3A11%3A30Z", fakeApi.lastRequestUrl)
        assertEquals(3, dataStore.count(Order::class.java).get().value())

        val rlm3 = dataStore.select(ResourceLastModified::class.java).where(ResourceLastModified.RESOURCE.eq("orders_withpdfdata")).get().first()
        assertEquals(rlm3.getEvent_slug(), "demo")
        assertEquals(rlm3.getLast_modified(), "timestamp1")
        assertEquals(rlm3.getStatus(), "incomplete:2019-01-01T00:15:15Z")


        fakeApi.fetchResponses.add {
            val respdata = JSONObject()
            respdata.put("count", 2)
            respdata.put("next", JSONObject.NULL)
            val results = JSONArray()
            results.put(jsonResource("orders/order3.json"))
            results.put(jsonResource("orders/order4.json"))
            respdata.put("results", results)
            fakeApi.ApiResponse(respdata, response(respdata, "timestamp4"))
        }

        osa.download()
        assertEquals("http://1.1.1.1/api/v1/organizers/demo/events/demo/orders/?testmode=false&exclude=downloads&exclude=payment_date&exclude=payment_provider&exclude=fees&exclude=positions.downloads&exclude=payments&exclude=refunds&pdf_data=true&ordering=datetime&created_since=2019-01-01T00%3A15%3A15Z", fakeApi.lastRequestUrl)
        assertEquals(4, dataStore.count(Order::class.java).get().value())

        val rlm2 = dataStore.select(ResourceLastModified::class.java).where(ResourceLastModified.RESOURCE.eq("orders_withpdfdata")).get().first()
        assertEquals(rlm2.getEvent_slug(), "demo")
        assertEquals(rlm2.getLast_modified(), "timestamp1")
        assertEquals(rlm2.getStatus(), "complete")
    }

    @Test
    fun testSimpleDiff() {
        fakeApi.fetchResponses.add {
            val respdata = JSONObject()
            respdata.put("count", 4)
            respdata.put("next", JSONObject.NULL)
            val results = JSONArray()
            results.put(jsonResource("orders/order1.json"))
            results.put(jsonResource("orders/order2.json"))
            results.put(jsonResource("orders/order3.json"))
            results.put(jsonResource("orders/order4.json"))
            respdata.put("results", results)
            fakeApi.ApiResponse(respdata, response(respdata, "timestamp1"))
        }

        osa.download()
        assertEquals(4, dataStore.count(Order::class.java).get().value())
        assertEquals(9, dataStore.count(OrderPosition::class.java).get().value())
        assertEquals(2, dataStore.count(CheckIn::class.java).get().value())

        fakeApi.fetchResponses.add {
            val respdata = JSONObject()
            respdata.put("count", 2)
            respdata.put("next", JSONObject.NULL)
            val results = JSONArray()
            results.put(jsonResource("orders/order1-modified.json"))
            results.put(jsonResource("orders/order5.json"))
            respdata.put("results", results)
            fakeApi.ApiResponse(respdata, response(respdata, "timestamp2"))
        }

        osa.download()
        assertEquals("http://1.1.1.1/api/v1/organizers/demo/events/demo/orders/?testmode=false&exclude=downloads&exclude=payment_date&exclude=payment_provider&exclude=fees&exclude=positions.downloads&exclude=payments&exclude=refunds&pdf_data=true&ordering=-last_modified&modified_since=timestamp1", fakeApi.lastRequestUrl)
        assertEquals(11, dataStore.count(OrderPosition::class.java).get().value())
        assertEquals(3, dataStore.count(CheckIn::class.java).get().value())
        val rlm = dataStore.select(ResourceLastModified::class.java).where(ResourceLastModified.RESOURCE.eq("orders_withpdfdata")).get().first()
        assertEquals(rlm.getEvent_slug(), "demo")
        assertEquals(rlm.getLast_modified(), "timestamp2")
        assertEquals(rlm.getStatus(), "complete")
    }

    @Test
    fun testDiffInterruptedRetry() {
        fakeApi.fetchResponses.add {
            val respdata = JSONObject()
            respdata.put("count", 4)
            respdata.put("next", JSONObject.NULL)
            val results = JSONArray()
            results.put(jsonResource("orders/order1.json"))
            results.put(jsonResource("orders/order2.json"))
            results.put(jsonResource("orders/order3.json"))
            results.put(jsonResource("orders/order4.json"))
            respdata.put("results", results)
            fakeApi.ApiResponse(respdata, response(respdata, "timestamp1"))
        }

        osa.download()
        assertEquals(4, dataStore.count(Order::class.java).get().value())
        assertEquals(2, dataStore.count(CheckIn::class.java).get().value())

        fakeApi.fetchResponses.add {
            val respdata = JSONObject()
            respdata.put("count", 2)
            respdata.put("next", "%page2")
            val results = JSONArray()
            results.put(jsonResource("orders/order1-modified.json"))
            respdata.put("results", results)
            fakeApi.ApiResponse(respdata, response(respdata, "timestamp2"))
        }
        fakeApi.fetchResponses.add { throw ApiException("Unreachable!") }

        try {
            osa.download()
            fail("Expected ApiException to be thrown")
        } catch (e: ApiException) {
        }

        assertEquals("%page2?testmode=false&exclude=downloads&exclude=payment_date&exclude=payment_provider&exclude=fees&exclude=positions.downloads&exclude=payments&exclude=refunds&pdf_data=true&ordering=-last_modified&modified_since=timestamp1", fakeApi.lastRequestUrl)
        assertEquals(4, dataStore.count(Order::class.java).get().value())
        assertEquals(3, dataStore.count(CheckIn::class.java).get().value())

        val rlm = dataStore.select(ResourceLastModified::class.java).where(ResourceLastModified.RESOURCE.eq("orders_withpdfdata")).get().first()
        assertEquals(rlm.getEvent_slug(), "demo")
        assertEquals(rlm.getLast_modified(), "timestamp1")
        assertEquals(rlm.getStatus(), "complete")

        fakeApi.fetchResponses.add {
            val respdata = JSONObject()
            respdata.put("count", 2)
            respdata.put("next", JSONObject.NULL)
            val results = JSONArray()
            results.put(jsonResource("orders/order1-modified.json"))
            results.put(jsonResource("orders/order5.json"))
            respdata.put("results", results)
            fakeApi.ApiResponse(respdata, response(respdata, "timestamp2"))
        }

        osa.download()
        assertEquals("http://1.1.1.1/api/v1/organizers/demo/events/demo/orders/?testmode=false&exclude=downloads&exclude=payment_date&exclude=payment_provider&exclude=fees&exclude=positions.downloads&exclude=payments&exclude=refunds&pdf_data=true&ordering=-last_modified&modified_since=timestamp1", fakeApi.lastRequestUrl)
        assertEquals(11, dataStore.count(OrderPosition::class.java).get().value())
        assertEquals(3, dataStore.count(CheckIn::class.java).get().value())
        val rlm2 = dataStore.select(ResourceLastModified::class.java).where(ResourceLastModified.RESOURCE.eq("orders_withpdfdata")).get().first()
        assertEquals(rlm2.getEvent_slug(), "demo")
        assertEquals(rlm2.getLast_modified(), "timestamp2")
        assertEquals(rlm2.getStatus(), "complete")
    }
}
