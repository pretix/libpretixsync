package eu.pretix.libpretixsync.check

import eu.pretix.libpretixsync.db.BaseDatabaseTest
import eu.pretix.libpretixsync.sync.CheckInListSyncAdapter
import eu.pretix.libpretixsync.sync.EventSyncAdapter
import eu.pretix.libpretixsync.sync.ItemSyncAdapter
import eu.pretix.libpretixsync.sync.OrderSyncAdapter
import eu.pretix.libpretixsync.sync.ReusableMediaSyncAdapter
import eu.pretix.pretixscan.scanproxy.tests.test.FakeConfigStore
import eu.pretix.pretixscan.scanproxy.tests.test.FakeFileStorage
import eu.pretix.pretixscan.scanproxy.tests.test.FakePretixApi
import eu.pretix.pretixscan.scanproxy.tests.test.jsonResource
import org.junit.Before
import org.junit.Test

import org.junit.Assert.assertEquals

class AsyncCheckProviderReusableMediumTest : BaseDatabaseTest() {
    private var configStore: FakeConfigStore? = null
    private var fakeApi: FakePretixApi? = null
    private var p: AsyncCheckProvider? = null

    @Before
    fun setUpFakes() {
        configStore = FakeConfigStore("mtrmt", "event1")
        fakeApi = FakePretixApi("mtrmt")
        p = AsyncCheckProvider(configStore!!, db)

        EventSyncAdapter(db, "event1", "event1", fakeApi!!, "", null).standaloneRefreshFromJSON(jsonResource("events/rmevent1.json"))
        EventSyncAdapter(db, "event2", "event2", fakeApi!!, "", null).standaloneRefreshFromJSON(jsonResource("events/rmevent2.json"))
        ItemSyncAdapter(db, FakeFileStorage(), "event1", fakeApi!!, "", null).standaloneRefreshFromJSON(jsonResource("items/rmevent1-item1.json"))
        ItemSyncAdapter(db, FakeFileStorage(), "event2", fakeApi!!, "", null).standaloneRefreshFromJSON(jsonResource("items/rmevent2-item1.json"))
        CheckInListSyncAdapter(db, FakeFileStorage(), "event1", fakeApi!!, "", null, 0).standaloneRefreshFromJSON(
            jsonResource("checkinlists/rmevent1-list1.json")
        )
        val rmsa = ReusableMediaSyncAdapter(db, FakeFileStorage(), fakeApi!!, "", null)
        rmsa.standaloneRefreshFromJSON(jsonResource("reusablemedia/mtrmt-medium1.json"))
        rmsa.standaloneRefreshFromJSON(jsonResource("reusablemedia/mtrmt-medium2.json"))
        rmsa.standaloneRefreshFromJSON(jsonResource("reusablemedia/mtrmt-medium3.json"))
        rmsa.standaloneRefreshFromJSON(jsonResource("reusablemedia/mtrmt-medium4.json"))

        val osa = OrderSyncAdapter(db, FakeFileStorage(), "event1", 0, true, false, fakeApi!!, "", null)
        osa.standaloneRefreshFromJSON(jsonResource("orders/rmevent1-order1.json"))
        val osa2 = OrderSyncAdapter(db, FakeFileStorage(), "event2", 0, true, false, fakeApi!!, "", null)
        osa2.standaloneRefreshFromJSON(jsonResource("orders/rmevent2-order1.json"))
    }

    @Test
    fun testTwoTicketsTimesOverlappingSameEvent() {
        val r = p!!.check(mapOf("event1" to 35L), "1111")
        assertEquals(TicketCheckProvider.CheckResult.Type.AMBIGUOUS, r.type)
    }

    @Test
    fun testTwoTicketsTimesOverlappingDifferentEvents() {
        val r = p!!.check(mapOf("event1" to 35L), "2222")
        assertEquals(TicketCheckProvider.CheckResult.Type.AMBIGUOUS, r.type)
    }

    @Test
    fun testTwoTicketsTimesNonOverlappingSameEvent() {
        val r = p!!.check(mapOf("event1" to 35L), "3333")
        assertEquals(TicketCheckProvider.CheckResult.Type.AMBIGUOUS, r.type)
    }

    @Test
    fun testTwoTicketsTimesNonOverlappingDifferentEvents() {
        val r = p!!.check(mapOf("event1" to 35L), "4444")
        assertEquals(TicketCheckProvider.CheckResult.Type.AMBIGUOUS, r.type)
    }
}
