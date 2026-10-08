package eu.pretix.libpretixsync.check

import eu.pretix.libpretixsync.db.BaseDatabaseTest
import eu.pretix.libpretixsync.sqldelight.QueuedCall
import eu.pretix.libpretixsync.sync.CheckInListSyncAdapter
import eu.pretix.libpretixsync.sync.EventSyncAdapter
import eu.pretix.libpretixsync.sync.ItemSyncAdapter
import eu.pretix.libpretixsync.sync.OrderSyncAdapter
import eu.pretix.libpretixsync.sync.ReusableMediaSyncAdapter
import eu.pretix.pretixscan.scanproxy.tests.test.FakeConfigStore
import eu.pretix.pretixscan.scanproxy.tests.test.FakeFileStorage
import eu.pretix.pretixscan.scanproxy.tests.test.FakePretixApi
import eu.pretix.pretixscan.scanproxy.tests.test.jsonResource
import org.joda.time.format.ISODateTimeFormat
import org.json.JSONObject
import org.junit.Before
import org.junit.Test

import org.junit.Assert.assertEquals

class AsyncCheckProviderAddonMatchGapTest : BaseDatabaseTest() {
    private var configStore: FakeConfigStore? = null
    private var fakeApi: FakePretixApi? = null
    private var p: AsyncCheckProvider? = null

    @Before
    fun setUpFakes() {
        configStore = FakeConfigStore("mtrmt", "event1")
        fakeApi = FakePretixApi("mtrmt")
        p = AsyncCheckProvider(configStore!!, db)

        EventSyncAdapter(db, FakeFileStorage(),"event1", "event1", fakeApi!!, "", null).standaloneRefreshFromJSON(jsonResource("events/rmevent1.json"))
        ItemSyncAdapter(db, FakeFileStorage(), "event1", fakeApi!!, "", null).standaloneRefreshFromJSON(jsonResource("items/rmevent1-item1.json"))
        ItemSyncAdapter(db, FakeFileStorage(), "event1", fakeApi!!, "", null).standaloneRefreshFromJSON(jsonResource("items/rmevent1-item2.json"))
        CheckInListSyncAdapter(db, FakeFileStorage(), "event1", fakeApi!!, "", null, 0).standaloneRefreshFromJSON(
            jsonResource("checkinlists/rmevent1-list2.json")
        )

        val osa = OrderSyncAdapter(db, FakeFileStorage(), "event1", 0, true, false, fakeApi!!, "", null)
        osa.standaloneRefreshFromJSON(jsonResource("orders/rmevent1-order2.json"))

        val rmsa = ReusableMediaSyncAdapter(db, FakeFileStorage(), fakeApi!!, "", null)
        rmsa.standaloneRefreshFromJSON(jsonResource("reusablemedia/mtrmt-medium10.json"))

        p!!.setNow(ISODateTimeFormat.dateTime().parseDateTime("2026-08-01T00:00:01.000Z"))
    }

    @Test
    fun testTwoAddonsGapEntryScanReportsAddonThatWorksNext() {
        assertEquals(0, db.queuedCallQueries.count().executeAsOne())
        val r = p!!.check(mapOf("event1" to 36L), "0000")
        assertEquals(TicketCheckProvider.CheckResult.Type.INVALID_TIME, r.type)
        assertEquals("ADDMT-4", r.orderCodeAndPositionId())
        assertEquals("Add-on ticket", r.ticket)

        assertEquals(1, db.queuedCallQueries.count().executeAsOne())
        val queuedCall : QueuedCall = db.queuedCallQueries.selectAll().executeAsList().first()
        val data = JSONObject(queuedCall.body)
        assertEquals("invalid_time", data.getString("error_reason"))
        assertEquals("0000", data.getString("raw_barcode"))
    }

    @Test
    fun testTwoAddonsGapExitScanIsValid() {
        val r = p!!.check(mapOf("event1" to 36L), "0000", "barcode", null, false, false, TicketCheckProvider.CheckInType.EXIT)
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        assertEquals("ADDMT-4", r.orderCodeAndPositionId())
        assertEquals("Add-on ticket", r.ticket)
    }
}
