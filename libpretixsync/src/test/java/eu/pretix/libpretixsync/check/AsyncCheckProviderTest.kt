package eu.pretix.libpretixsync.check

import eu.pretix.libpretixsync.db.*
import eu.pretix.libpretixsync.sync.*
import eu.pretix.libpretixsync.test.*
import org.json.JSONException
import org.junit.Before
import org.junit.Test

import java.util.ArrayList
import java.util.Date

import org.junit.Assert.assertEquals

class AsyncCheckProviderTest : BaseDatabaseTest() {
    private var configStore: FakeConfigStore? = null
    private var fakeApi: FakePretixApi? = null
    private var item: Item? = null
    private var p: AsyncCheckProvider? = null

    @Before
    fun setUpFakes() {
        configStore = FakeConfigStore()
        fakeApi = FakePretixApi()
        p = AsyncCheckProvider(configStore, dataStore, 1L)

        ItemSyncAdapter(dataStore, FakeFileStorage(), "demo", fakeApi, null).standaloneRefreshFromJSON(jsonResource("items/item1.json"))
        ItemSyncAdapter(dataStore, FakeFileStorage(), "demo", fakeApi, null).standaloneRefreshFromJSON(jsonResource("items/item2.json"))
        CheckInListSyncAdapter(dataStore, FakeFileStorage(), "demo", fakeApi, null).standaloneRefreshFromJSON(jsonResource("checkinlists/list1.json"))

        val osa = OrderSyncAdapter(dataStore, FakeFileStorage(), "demo", fakeApi, null)
        osa.standaloneRefreshFromJSON(jsonResource("orders/order1.json"))
        osa.standaloneRefreshFromJSON(jsonResource("orders/order2.json"))
        osa.standaloneRefreshFromJSON(jsonResource("orders/order3.json"))
    }

    @Test
    fun testSimpleSuccess() {
        val r = p!!.check("kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        assertEquals("Regular ticket", r.ticket)
        assertEquals(null, r.variation)
        assertEquals("Casey Flores", r.attendee_name)
        assertEquals(true, r.isRequireAttention)

        val qciList = dataStore.select(QueuedCheckIn::class.java).get().toList()
        assertEquals(1, qciList.size.toLong())
        assertEquals("kfndgffgyw4tdgcacx6bb3bgemq69cxj", qciList[0].getSecret())
    }

    @Test
    fun testSimpleInvalid() {
        val r = p!!.check("abc")
        assertEquals(TicketCheckProvider.CheckResult.Type.INVALID, r.type)
    }

    @Test
    fun testSimpleUnpaid() {
        val r = p!!.check("h4t6w9ykuea4n5zaapy648y2dcfg8weq")
        assertEquals(TicketCheckProvider.CheckResult.Type.UNPAID, r.type)
        assertEquals("Regular ticket", r.ticket)
        assertEquals(null, r.variation)
        assertEquals("Emily Scott", r.attendee_name)
        assertEquals(true, r.isRequireAttention)
    }

    @Test
    fun testSimpleUnpaidAllowed() {
        var r = p!!.check("h4t6w9ykuea4n5zaapy648y2dcfg8weq")
        assertEquals(TicketCheckProvider.CheckResult.Type.UNPAID, r.type)
        assertEquals("Regular ticket", r.ticket)
        assertEquals(null, r.variation)
        assertEquals("Emily Scott", r.attendee_name)
        assertEquals(true, r.isRequireAttention)

        r = p!!.check("h4t6w9ykuea4n5zaapy648y2dcfg8weq", null, true, false)
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        assertEquals("Regular ticket", r.ticket)
        assertEquals(null, r.variation)
        assertEquals("Emily Scott", r.attendee_name)
        assertEquals(true, r.isRequireAttention)
    }

    @Test
    fun testSimpleRedeemed() {
        val r = p!!.check("g2sc5ym78h5q5y5sbswses2b5h8pp6kt")
        assertEquals(TicketCheckProvider.CheckResult.Type.USED, r.type)
    }

    @Test
    fun testSimpleRedeemedLocally() {
        var r = p!!.check("kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
        assertEquals("Regular ticket", r.ticket)
        assertEquals(null, r.variation)
        assertEquals("Casey Flores", r.attendee_name)
        assertEquals(true, r.isRequireAttention)

        r = p!!.check("kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.USED, r.type)
        assertEquals("Regular ticket", r.ticket)
        assertEquals(null, r.variation)
        assertEquals("Casey Flores", r.attendee_name)
        assertEquals(true, r.isRequireAttention)
    }

    @Test
    fun testQuestionsForOtherItem() {
        QuestionSyncAdapter(dataStore, FakeFileStorage(), "demo", fakeApi, null).standaloneRefreshFromJSON(jsonResource("questions/question1.json"))

        val r = p!!.check("fem3hggggag8q38qkx35c2panqr5xjq8")
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)
    }

    @Test
    fun testQuestionsRequired() {
        QuestionSyncAdapter(dataStore, FakeFileStorage(), "demo", fakeApi, null).standaloneRefreshFromJSON(jsonResource("questions/question1.json"))

        var r = p!!.check("kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.ANSWERS_REQUIRED, r.type)
        assertEquals(1, r.requiredAnswers.size.toLong())
        val ra = r.requiredAnswers[0]
        assertEquals(1, ra.question.getServer_id())

        val answers = ArrayList<TicketCheckProvider.Answer>()
        answers.add(TicketCheckProvider.Answer(ra.question, "True"))

        r = p!!.check("kfndgffgyw4tdgcacx6bb3bgemq69cxj", answers, false, false)
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.type)

        val qciList = dataStore.select(QueuedCheckIn::class.java).get().toList()
        assertEquals(1, qciList.size.toLong())
        assertEquals("kfndgffgyw4tdgcacx6bb3bgemq69cxj", qciList[0].getSecret())
        assertEquals("[{\"answer\":\"True\",\"question\":1}]", qciList[0].getAnswers())
    }

    @Test
    fun testQuestionsInvalidInput() {
        QuestionSyncAdapter(dataStore, FakeFileStorage(), "demo", fakeApi, null).standaloneRefreshFromJSON(jsonResource("questions/question2.json"))

        var r = p!!.check("kfndgffgyw4tdgcacx6bb3bgemq69cxj")
        assertEquals(TicketCheckProvider.CheckResult.Type.ANSWERS_REQUIRED, r.type)
        assertEquals(1, r.requiredAnswers.size.toLong())
        val ra = r.requiredAnswers[0]

        val answers = ArrayList<TicketCheckProvider.Answer>()
        answers.add(TicketCheckProvider.Answer(ra.question, "True"))

        r = p!!.check("kfndgffgyw4tdgcacx6bb3bgemq69cxj", answers, false, false)
        assertEquals(TicketCheckProvider.CheckResult.Type.ANSWERS_REQUIRED, r.type)

        val qciList = dataStore.select(QueuedCheckIn::class.java).get().toList()
        assertEquals(0, qciList.size.toLong())
    }

    @Test
    @Throws(CheckException::class)
    fun testSearch() {
        configStore!!.setAllow_search(true)

        // Short searches are empty
        var srList: List<TicketCheckProvider.SearchResult> = p!!.search("foo", 1)
        assertEquals(0, srList.size.toLong())

        // Search by secret
        srList = p!!.search("kFNDgffgyw4", 1)
        assertEquals(1, srList.size.toLong())
        srList = p!!.search("baaaaar", 1)
        assertEquals(0, srList.size.toLong())

        // Search by name
        srList = p!!.search("Einstein", 1)
        assertEquals(0, srList.size.toLong())
        srList = p!!.search("WATSON", 1)
        assertEquals(1, srList.size.toLong())

        // Search by email
        srList = p!!.search("foobar@example.org", 1)
        assertEquals(0, srList.size.toLong())
        srList = p!!.search("holmesConnie@kelly.com", 1)
        assertEquals(3, srList.size.toLong())

        // Search by order code
        srList = p!!.search("AAAAA", 1)
        assertEquals(0, srList.size.toLong())
        srList = p!!.search("Vh3d3", 1)
        assertEquals(3, srList.size.toLong())

        val r = srList[0]
        assertEquals("Regular ticket", r.ticket)
        assertEquals(null, r.variation)
        assertEquals("Casey Flores", r.attendee_name)
        assertEquals("kfndgffgyw4tdgcacx6bb3bgemq69cxj", r.secret)
        assertEquals(false, r.isRedeemed)
        assertEquals(true, r.isPaid)
        assertEquals(true, r.isRequireAttention)
        assertEquals("Merch", srList[1].ticket)
        assertEquals(true, srList[1].isRedeemed)
        assertEquals(false, srList[1].isRequireAttention)
    }

    @Test
    @Throws(CheckException::class)
    fun testSearchRestricted() {
        configStore!!.setAllow_search(false)

        // Short searches are empty
        var srList: List<TicketCheckProvider.SearchResult> = p!!.search("foo", 1)
        assertEquals(0, srList.size.toLong())

        // Search by secret
        srList = p!!.search("kFNDgffgyw4", 1)
        assertEquals(1, srList.size.toLong())
        srList = p!!.search("baaaaar", 1)
        assertEquals(0, srList.size.toLong())

        // Search by name
        srList = p!!.search("Einstein", 1)
        assertEquals(0, srList.size.toLong())
        srList = p!!.search("WATSON", 1)
        assertEquals(0, srList.size.toLong())
    }

    @Test
    @Throws(JSONException::class, CheckException::class)
    fun testStatusInfo() {
        val sr = p!!.status()
        assertEquals("All", sr.eventName)
        assertEquals(7.toLong(), sr.totalTickets.toLong())
        assertEquals(2.toLong(), sr.alreadyScanned.toLong())
        assertEquals(2, sr.items.size.toLong())
        val i = sr.items[0]
        assertEquals(1.toLong(), i.id)
        assertEquals(3.toLong(), i.total.toLong())
        assertEquals(1.toLong(), i.checkins.toLong())
        assertEquals(true, i.isAdmission)
        assertEquals(0, i.variations.size.toLong())
    }
}
