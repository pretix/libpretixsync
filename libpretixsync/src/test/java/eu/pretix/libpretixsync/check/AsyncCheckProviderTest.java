package eu.pretix.libpretixsync.check;

import eu.pretix.libpretixsync.db.*;
import eu.pretix.libpretixsync.test.FakeConfigStore;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class AsyncCheckProviderTest extends BaseDatabaseTest {
    private FakeConfigStore configStore;

    @Before
    public void setUpFakes() {
        configStore = new FakeConfigStore();
    }

    @Test
    public void testSimpleSuccess() {
        AsyncCheckProvider p = new AsyncCheckProvider(configStore, dataStore);

        Ticket ticket = new Ticket();
        ticket.setRedeemed(false);
        ticket.setSecret("foooooo");
        ticket.setItem("Standard Ticket");
        ticket.setAttendee_name("Niels Bohr");
        ticket.setOrder("12345");
        ticket.setPaid(true);
        ticket.setRequire_attention(false);
        dataStore.insert(ticket);

        TicketCheckProvider.CheckResult r = p.check("foooooo");
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.getType());
        assertEquals("Standard Ticket", r.getTicket());
        assertEquals(null, r.getVariation());
        assertEquals("Niels Bohr", r.getAttendee_name());
        assertEquals(false, r.isRequireAttention());

        List<QueuedCheckIn> qciList = dataStore.select(QueuedCheckIn.class).get().toList();
        assertEquals(1, qciList.size());
        assertEquals("foooooo", qciList.get(0).getSecret());
    }

    @Test
    public void testSimpleInvalid() {
        AsyncCheckProvider p = new AsyncCheckProvider(configStore, dataStore);

        Ticket ticket = new Ticket();
        ticket.setRedeemed(false);
        ticket.setSecret("foooooo");
        ticket.setItem("Standard Ticket");
        ticket.setAttendee_name("Niels Bohr");
        ticket.setOrder("12345");
        ticket.setRequire_attention(false);
        dataStore.insert(ticket);

        TicketCheckProvider.CheckResult r = p.check("abc");
        assertEquals(TicketCheckProvider.CheckResult.Type.INVALID, r.getType());
    }

    @Test
    public void testSimpleUnpaid() {
        AsyncCheckProvider p = new AsyncCheckProvider(configStore, dataStore);

        Ticket ticket = new Ticket();
        ticket.setRedeemed(false);
        ticket.setSecret("foooooo");
        ticket.setItem("Standard Ticket");
        ticket.setAttendee_name("Niels Bohr");
        ticket.setOrder("12345");
        ticket.setPaid(false);
        ticket.setRequire_attention(false);
        dataStore.insert(ticket);

        TicketCheckProvider.CheckResult r = p.check("foooooo");
        assertEquals(TicketCheckProvider.CheckResult.Type.UNPAID, r.getType());
        assertEquals("Standard Ticket", r.getTicket());
        assertEquals(null, r.getVariation());
        assertEquals("Niels Bohr", r.getAttendee_name());
        assertEquals(false, r.isRequireAttention());
    }

    @Test
    public void testSimpleRedeemed() {
        AsyncCheckProvider p = new AsyncCheckProvider(configStore, dataStore);

        Ticket ticket = new Ticket();
        ticket.setRedeemed(true);
        ticket.setSecret("foooooo");
        ticket.setItem("Standard Ticket");
        ticket.setAttendee_name("Niels Bohr");
        ticket.setOrder("12345");
        ticket.setPaid(true);
        ticket.setRequire_attention(false);
        dataStore.insert(ticket);

        TicketCheckProvider.CheckResult r = p.check("foooooo");
        assertEquals(TicketCheckProvider.CheckResult.Type.USED, r.getType());
        assertEquals("Standard Ticket", r.getTicket());
        assertEquals(null, r.getVariation());
        assertEquals("Niels Bohr", r.getAttendee_name());
        assertEquals(false, r.isRequireAttention());
    }

    @Test
    public void testSimpleRedeemedLocally() {
        AsyncCheckProvider p = new AsyncCheckProvider(configStore, dataStore);

        Ticket ticket = new Ticket();
        ticket.setRedeemed(false);
        ticket.setSecret("foooooo");
        ticket.setItem("Standard Ticket");
        ticket.setAttendee_name("Niels Bohr");
        ticket.setOrder("12345");
        ticket.setPaid(true);
        ticket.setRequire_attention(false);
        dataStore.insert(ticket);

        QueuedCheckIn queuedCheckIn = new QueuedCheckIn();
        queuedCheckIn.setSecret(ticket.getSecret());
        queuedCheckIn.setDatetime(new Date());
        queuedCheckIn.setNonce(NonceGenerator.nextNonce());
        dataStore.insert(queuedCheckIn);

        TicketCheckProvider.CheckResult r = p.check("foooooo");
        assertEquals(TicketCheckProvider.CheckResult.Type.USED, r.getType());
        assertEquals("Standard Ticket", r.getTicket());
        assertEquals(null, r.getVariation());
        assertEquals("Niels Bohr", r.getAttendee_name());
        assertEquals(false, r.isRequireAttention());

        List<QueuedCheckIn> qciList = dataStore.select(QueuedCheckIn.class).get().toList();
        assertEquals(1, qciList.size());
        assertEquals("foooooo", qciList.get(0).getSecret());
    }

    @Test
    public void testQuestionsForOtherItem() {
        AsyncCheckProvider p = new AsyncCheckProvider(configStore, dataStore);

        Ticket ticket = new Ticket();
        ticket.setRedeemed(false);
        ticket.setSecret("foooooo");
        ticket.setItem("Standard Ticket");
        ticket.setItem_id(12L);
        ticket.setAttendee_name("Niels Bohr");
        ticket.setOrder("12345");
        ticket.setPaid(true);
        ticket.setRequire_attention(false);
        dataStore.insert(ticket);

        Item item = new Item();
        item.setServer_id(13L);
        dataStore.insert(item);

        Question q = new Question();
        q.setQuestion("Really?");
        q.setType(QuestionType.B);
        q.setServer_id(14L);
        q.getItems().add(item);
        dataStore.insert(q);

        TicketCheckProvider.CheckResult r = p.check("foooooo");
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.getType());
    }

    @Test
    public void testQuestionsRequired() {
        AsyncCheckProvider p = new AsyncCheckProvider(configStore, dataStore);

        Ticket ticket = new Ticket();
        ticket.setRedeemed(false);
        ticket.setSecret("foooooo");
        ticket.setItem("Standard Ticket");
        ticket.setItem_id(13L);
        ticket.setAttendee_name("Niels Bohr");
        ticket.setOrder("12345");
        ticket.setPaid(true);
        ticket.setRequire_attention(false);
        dataStore.insert(ticket);

        Item item = new Item();
        item.setServer_id(13L);
        dataStore.insert(item);

        Question q = new Question();
        q.setQuestion("Really?");
        q.setType(QuestionType.B);
        q.setServer_id(14L);
        q.getItems().add(item);
        dataStore.insert(q);

        TicketCheckProvider.CheckResult r = p.check("foooooo");
        assertEquals(TicketCheckProvider.CheckResult.Type.ANSWERS_REQUIRED, r.getType());
        assertEquals(1, r.getRequiredAnswers().size());
        TicketCheckProvider.RequiredAnswer ra = r.getRequiredAnswers().get(0);
        assertEquals(q.getServer_id(), ra.getQuestion().getServer_id());

        List<TicketCheckProvider.Answer> answers = new ArrayList<>();
        answers.add(new TicketCheckProvider.Answer(q, "True"));

        r = p.check(ticket.getSecret(), answers);
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.getType());

        List<QueuedCheckIn> qciList = dataStore.select(QueuedCheckIn.class).get().toList();
        assertEquals(1, qciList.size());
        assertEquals("foooooo", qciList.get(0).getSecret());
        assertEquals("[{\"answer\":\"True\",\"question\":14}]", qciList.get(0).getAnswers());
    }

    @Test
    public void testQuestionsInvalidInput() {
        AsyncCheckProvider p = new AsyncCheckProvider(configStore, dataStore);

        Ticket ticket = new Ticket();
        ticket.setRedeemed(false);
        ticket.setSecret("foooooo");
        ticket.setItem("Standard Ticket");
        ticket.setItem_id(13L);
        ticket.setAttendee_name("Niels Bohr");
        ticket.setOrder("12345");
        ticket.setPaid(true);
        ticket.setRequire_attention(false);
        dataStore.insert(ticket);

        Item item = new Item();
        item.setServer_id(13L);
        dataStore.insert(item);

        Question q = new Question();
        q.setQuestion("Your age");
        q.setType(QuestionType.N);
        q.setServer_id(14L);
        q.getItems().add(item);
        dataStore.insert(q);

        TicketCheckProvider.CheckResult r = p.check("foooooo");
        assertEquals(TicketCheckProvider.CheckResult.Type.ANSWERS_REQUIRED, r.getType());
        assertEquals(1, r.getRequiredAnswers().size());
        TicketCheckProvider.RequiredAnswer ra = r.getRequiredAnswers().get(0);
        assertEquals(q.getServer_id(), ra.getQuestion().getServer_id());

        List<TicketCheckProvider.Answer> answers = new ArrayList<>();
        answers.add(new TicketCheckProvider.Answer(q, "True"));

        r = p.check(ticket.getSecret(), answers);
        assertEquals(TicketCheckProvider.CheckResult.Type.ANSWERS_REQUIRED, r.getType());

        List<QueuedCheckIn> qciList = dataStore.select(QueuedCheckIn.class).get().toList();
        assertEquals(0, qciList.size());
    }

    @Test
    public void testSearch() throws CheckException {
        AsyncCheckProvider p = new AsyncCheckProvider(configStore, dataStore);

        Ticket ticket = new Ticket();
        ticket.setRedeemed(false);
        ticket.setSecret("foooooo");
        ticket.setItem("Standard Ticket");
        ticket.setItem_id(13L);
        ticket.setAttendee_name("Niels Bohr");
        ticket.setOrder("12345");
        ticket.setPaid(true);
        ticket.setRequire_attention(false);
        dataStore.insert(ticket);

        configStore.setAllow_search(true);

        // Short searches are empty
        List<TicketCheckProvider.SearchResult> srList = p.search("foo");
        assertEquals(0, srList.size());

        // Search by secret
        srList = p.search("foooo");
        assertEquals(1, srList.size());
        srList = p.search("baaaaar");
        assertEquals(0, srList.size());

        // Search by name
        srList = p.search("Albert");
        assertEquals(0, srList.size());
        srList = p.search("Niels Bohr");
        assertEquals(1, srList.size());

        // Search by order code
        srList = p.search("54321");
        assertEquals(0, srList.size());
        srList = p.search("12345");
        assertEquals(1, srList.size());

        TicketCheckProvider.SearchResult r = srList.get(0);
        assertEquals("Standard Ticket", r.getTicket());
        assertEquals(null, r.getVariation());
        assertEquals("Niels Bohr", r.getAttendee_name());
        assertEquals(ticket.getSecret(), r.getSecret());
        assertEquals(false, r.isRedeemed());
        assertEquals(true, r.isPaid());
        assertEquals(false, r.isRequireAttention());
    }

    @Test
    public void testSearchRestricted() throws CheckException {
        AsyncCheckProvider p = new AsyncCheckProvider(configStore, dataStore);

        Ticket ticket = new Ticket();
        ticket.setRedeemed(false);
        ticket.setSecret("foooooo");
        ticket.setItem("Standard Ticket");
        ticket.setItem_id(13L);
        ticket.setAttendee_name("Niels Bohr");
        ticket.setOrder("12345");
        ticket.setPaid(true);
        ticket.setRequire_attention(false);
        dataStore.insert(ticket);

        configStore.setAllow_search(false);

        // Short searches are empty
        List<TicketCheckProvider.SearchResult> srList = p.search("foo");
        assertEquals(0, srList.size());

        // Search by secret
        srList = p.search("foooo");
        assertEquals(1, srList.size());
        srList = p.search("baaaaar");
        assertEquals(0, srList.size());

        // Search by name is not allowed
        srList = p.search("Albert");
        assertEquals(0, srList.size());
        srList = p.search("Niels");
        assertEquals(0, srList.size());

        // Search by order code is not allowed
        srList = p.search("54321");
        assertEquals(0, srList.size());
        srList = p.search("12345");
        assertEquals(0, srList.size());
    }

    @Test
    public void testStatusInfo() throws JSONException, CheckException {
        AsyncCheckProvider p = new AsyncCheckProvider(configStore, dataStore);

        Ticket ticket = new Ticket();
        ticket.setRedeemed(false);
        ticket.setSecret("foooooo");
        ticket.setItem("Standard Ticket");
        ticket.setItem_id(5L);
        ticket.setAttendee_name("Niels Bohr");
        ticket.setOrder("12345");
        ticket.setVariation("Early Bird");
        ticket.setVariation_id(1L);
        ticket.setPaid(true);
        ticket.setRequire_attention(false);
        dataStore.insert(ticket);

        ticket = new Ticket();
        ticket.setSecret("baaaaaar");
        ticket.setItem("Standard Ticket");
        ticket.setItem_id(5L);
        ticket.setVariation("Early Bird");
        ticket.setVariation_id(1L);
        ticket.setAttendee_name("Niels Bohr");
        ticket.setOrder("12345");
        ticket.setPaid(true);
        ticket.setRedeemed(true);
        ticket.setRequire_attention(false);
        dataStore.insert(ticket);

        configStore.setLastStatusData("{\"status\": \"ok\"," +
                "\"event\": {\"name\": \"DemoCon\"}," +
                "\"total\": 10," +
                "\"checkins\": 5," +
                "\"items\": [{" +
                "    \"id\": 5," +
                "    \"name\": \"Standard Ticket\"," +
                "    \"total\": 10," +
                "    \"checkins\": 5," +
                "    \"admission\": true," +
                "    \"variations\": [{" +
                "       \"id\": 1," +
                "       \"name\": \"Early Bird\"," +
                "       \"total\": 10," +
                "       \"checkins\": 5" +
                "    }]" +
                "}]}");
        TicketCheckProvider.StatusResult sr = p.status();
        assertEquals("DemoCon", sr.getEventName());
        assertEquals((long) 2, (long) sr.getTotalTickets());
        assertEquals((long) 1, (long) sr.getAlreadyScanned());
        assertEquals(1, sr.getItems().size());
        TicketCheckProvider.StatusResultItem i = sr.getItems().get(0);
        assertEquals((long) 5, (long) i.getId());
        assertEquals((long) 2, (long) i.getTotal());
        assertEquals((long) 1, (long) i.getCheckins());
        assertEquals(true, i.isAdmission());
        assertEquals(1, i.getVariations().size());
        TicketCheckProvider.StatusResultItemVariation v = i.getVariations().get(0);
        assertEquals((long) 1, (long) v.getId());
        assertEquals((long) 2, (long) v.getTotal());
        assertEquals((long) 1, (long) v.getCheckins());
    }
}
