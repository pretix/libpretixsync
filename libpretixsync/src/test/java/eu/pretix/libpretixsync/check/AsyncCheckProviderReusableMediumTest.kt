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
import org.joda.time.format.ISODateTimeFormat
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
        CheckInListSyncAdapter(db, FakeFileStorage(), "event2", fakeApi!!, "", null, 0).standaloneRefreshFromJSON(
            jsonResource("checkinlists/rmevent2-list1.json")
        )

        val osa = OrderSyncAdapter(db, FakeFileStorage(), "event1", 0, true, false, fakeApi!!, "", null)
        osa.standaloneRefreshFromJSON(jsonResource("orders/rmevent1-order1.json"))
        val osa2 = OrderSyncAdapter(db, FakeFileStorage(), "event2", 0, true, false, fakeApi!!, "", null)
        osa2.standaloneRefreshFromJSON(jsonResource("orders/rmevent2-order1.json"))

        val rmsa = ReusableMediaSyncAdapter(db, FakeFileStorage(), fakeApi!!, "", null)
        rmsa.standaloneRefreshFromJSON(jsonResource("reusablemedia/mtrmt-medium1.json"))
        rmsa.standaloneRefreshFromJSON(jsonResource("reusablemedia/mtrmt-medium2.json"))
        rmsa.standaloneRefreshFromJSON(jsonResource("reusablemedia/mtrmt-medium3.json"))
        rmsa.standaloneRefreshFromJSON(jsonResource("reusablemedia/mtrmt-medium4.json"))
        rmsa.standaloneRefreshFromJSON(jsonResource("reusablemedia/mtrmt-medium5.json"))
        rmsa.standaloneRefreshFromJSON(jsonResource("reusablemedia/mtrmt-medium6.json"))
        rmsa.standaloneRefreshFromJSON(jsonResource("reusablemedia/mtrmt-medium7.json"))
    }

    @Test
    fun testMediumNotActive() {
        val r = p!!.check(mapOf("event1" to 35L), "5555")
        assertEquals(TicketCheckProvider.CheckResult.Type.INVALID, r.type)
    }

    @Test
    fun testMediumExpired() {
        p!!.setNow(ISODateTimeFormat.dateTime().parseDateTime("2026-01-01T00:00:01.000Z"))
        val r = p!!.check(mapOf("event1" to 35L), "6666")
        assertEquals(TicketCheckProvider.CheckResult.Type.INVALID, r.type)
    }

    @Test
    fun testTwoTicketsTimesOverlappingSameEvent() {
        val r = p!!.check(mapOf("event1" to 35L), "1111")
        assertEquals(TicketCheckProvider.CheckResult.Type.AMBIGUOUS, r.type)
    }

    @Test
    fun testTwoTicketsTimesOverlappingDifferentEvents() {
        val r = p!!.check(mapOf("event1" to 35L), "2222")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        assertEquals("Regular ticket", r.ticket)
        assertEquals("W0JKM", r.orderCode)
        assertEquals(1L, r.positionId)
    }

    @Test
    fun testTwoTicketsTimesNonOverlappingSameEvent() {
        p!!.setNow(ISODateTimeFormat.dateTime().parseDateTime("2026-01-01T00:00:01.000Z"))
        var r = p!!.check(mapOf("event1" to 35L), "3333")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        assertEquals("W0JKM", r.orderCode)
        assertEquals(1L, r.positionId)

        p!!.setNow(ISODateTimeFormat.dateTime().parseDateTime("2027-01-01T00:00:01.000Z"))
        r = p!!.check(mapOf("event1" to 35L), "3333")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        assertEquals("W0JKM", r.orderCode)
        assertEquals(3L, r.positionId)
    }

    @Test
    fun testTwoTicketsTimesNonOverlappingDifferentEvents() {
        p!!.setNow(ISODateTimeFormat.dateTime().parseDateTime("2026-01-05T00:00:01.000Z"))
        var r = p!!.check(mapOf("event1" to 35L), "4444")
        assertEquals(TicketCheckProvider.CheckResult.Type.INVALID_TIME, r.type)

        r = p!!.check(mapOf("event2" to 36L), "4444")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)

        p!!.setNow(ISODateTimeFormat.dateTime().parseDateTime("2027-01-05T00:00:01.000Z"))
        r = p!!.check(mapOf("event1" to 35L), "4444")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)

        r = p!!.check(mapOf("event2" to 36L), "4444")
        assertEquals(TicketCheckProvider.CheckResult.Type.INVALID_TIME, r.type)
    }

    @Test
    fun testTwoTicketsTimesNonOverlappingSpaceBetweenSameEvent() {
        p!!.setNow(ISODateTimeFormat.dateTime().parseDateTime("2026-01-01T00:00:01.000Z"))
        var r = p!!.check(mapOf("event1" to 35L), "7777")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        assertEquals("W0JKM-6", r.orderCodeAndPositionId())

        p!!.setNow(ISODateTimeFormat.dateTime().parseDateTime("2026-09-01T00:00:01.000Z"))
        r = p!!.check(mapOf("event1" to 35L), "7777")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        assertEquals("W0JKM-7", r.orderCodeAndPositionId())

        // use the candidate that will "work next"
        p!!.setNow(ISODateTimeFormat.dateTime().parseDateTime("2026-08-01T00:00:01.000Z"))
        r = p!!.check(mapOf("event1" to 35L), "7777")
        assertEquals(TicketCheckProvider.CheckResult.Type.INVALID_TIME, r.type)
        assertEquals("W0JKM-7", r.orderCodeAndPositionId())

        // no candidate in the future, use closest from the past
        p!!.setNow(ISODateTimeFormat.dateTime().parseDateTime("2027-01-01T00:00:01.000Z"))
        r = p!!.check(mapOf("event1" to 35L), "7777")
        assertEquals(TicketCheckProvider.CheckResult.Type.INVALID_TIME, r.type)
        assertEquals("W0JKM-7", r.orderCodeAndPositionId())
    }
}
