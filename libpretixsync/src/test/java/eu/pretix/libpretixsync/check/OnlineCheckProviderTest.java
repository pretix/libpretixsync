package eu.pretix.libpretixsync.check;

import eu.pretix.libpretixsync.DummySentryImplementation;
import eu.pretix.libpretixsync.api.DefaultHttpClientFactory;
import eu.pretix.libpretixsync.db.BaseDatabaseTest;
import eu.pretix.libpretixsync.db.QuestionOption;
import eu.pretix.libpretixsync.db.QueuedCheckIn;
import eu.pretix.libpretixsync.db.Ticket;
import eu.pretix.libpretixsync.sync.SyncException;
import eu.pretix.libpretixsync.sync.SyncManager;
import eu.pretix.libpretixsync.test.FakeConfigStore;
import eu.pretix.libpretixsync.test.FakePretixApi;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class OnlineCheckProviderTest extends BaseDatabaseTest {
    private FakeConfigStore configStore;
    private FakePretixApi api;

    @Before
    public void setUpFakes() {
        configStore = new FakeConfigStore();
        api = new FakePretixApi();
    }

    @Test
    public void testSimpleSuccess() throws JSONException {
        OnlineCheckProvider p = new OnlineCheckProvider(configStore, new DefaultHttpClientFactory());
        p.api = api;

        api.setNextRedeemResponse(new JSONObject("{\"status\": \"ok\"," +
                "\"data\": {" +
                "    \"item\": \"Standard Ticket\"," +
                "    \"variation\": null," +
                "    \"attendee_name\": \"Niels Bohr\"," +
                "    \"order\": \"12345\"," +
                "    \"attention\": false" +
                "}}"));
        TicketCheckProvider.CheckResult r = p.check("foooooo");
        assertEquals(TicketCheckProvider.CheckResult.Type.VALID, r.getType());
        assertEquals("Standard Ticket", r.getTicket());
        assertEquals("null", r.getVariation());
        assertEquals("Niels Bohr", r.getAttendee_name());
        assertEquals(false, r.isRequireAttention());
    }

    @Test
    public void testSimpleUnknown() throws JSONException {
        OnlineCheckProvider p = new OnlineCheckProvider(configStore, new DefaultHttpClientFactory());
        p.api = api;

        api.setNextRedeemResponse(new JSONObject("{\"status\": \"error\"," +
                "\"reason\": \"unknown_ticket\"" +
                "}"));
        TicketCheckProvider.CheckResult r = p.check("foooooo");
        assertEquals(TicketCheckProvider.CheckResult.Type.INVALID, r.getType());
        assertNull(r.getTicket());
    }

    @Test
    public void testSimpleWrongProduct() throws JSONException {
        OnlineCheckProvider p = new OnlineCheckProvider(configStore, new DefaultHttpClientFactory());
        p.api = api;

        api.setNextRedeemResponse(new JSONObject("{\"status\": \"error\"," +
                "\"reason\": \"product\"," +
                "\"data\": {" +
                "    \"item\": \"Standard Ticket\"," +
                "    \"variation\": null," +
                "    \"attendee_name\": \"Niels Bohr\"," +
                "    \"order\": \"12345\"," +
                "    \"attention\": false" +
                "}}"));
        TicketCheckProvider.CheckResult r = p.check("foooooo");
        assertEquals(TicketCheckProvider.CheckResult.Type.PRODUCT, r.getType());
        assertEquals("Standard Ticket", r.getTicket());
        assertEquals("null", r.getVariation());
        assertEquals("Niels Bohr", r.getAttendee_name());
        assertEquals(false, r.isRequireAttention());
    }

    @Test
    public void testSimpleUnpaid() throws JSONException {
        OnlineCheckProvider p = new OnlineCheckProvider(configStore, new DefaultHttpClientFactory());
        p.api = api;

        api.setNextRedeemResponse(new JSONObject("{\"status\": \"error\"," +
                "\"reason\": \"unpaid\"," +
                "\"data\": {" +
                "    \"item\": \"Standard Ticket\"," +
                "    \"variation\": null," +
                "    \"attendee_name\": \"Niels Bohr\"," +
                "    \"order\": \"12345\"," +
                "    \"attention\": false" +
                "}}"));
        TicketCheckProvider.CheckResult r = p.check("foooooo");
        assertEquals(TicketCheckProvider.CheckResult.Type.UNPAID, r.getType());
        assertEquals("Standard Ticket", r.getTicket());
        assertEquals("null", r.getVariation());
        assertEquals("Niels Bohr", r.getAttendee_name());
        assertEquals(false, r.isRequireAttention());
    }

    @Test
    public void testSimpleRedeemed() throws JSONException {
        OnlineCheckProvider p = new OnlineCheckProvider(configStore, new DefaultHttpClientFactory());
        p.api = api;

        api.setNextRedeemResponse(new JSONObject("{\"status\": \"error\"," +
                "\"reason\": \"already_redeemed\"," +
                "\"data\": {" +
                "    \"item\": \"Standard Ticket\"," +
                "    \"variation\": null," +
                "    \"attendee_name\": \"Niels Bohr\"," +
                "    \"order\": \"12345\"," +
                "    \"attention\": false" +
                "}}"));
        TicketCheckProvider.CheckResult r = p.check("foooooo");
        assertEquals(TicketCheckProvider.CheckResult.Type.USED, r.getType());
        assertEquals("Standard Ticket", r.getTicket());
        assertEquals("null", r.getVariation());
        assertEquals("Niels Bohr", r.getAttendee_name());
        assertEquals(false, r.isRequireAttention());
    }

    @Test
    public void testRequireQuestions() throws JSONException {
        OnlineCheckProvider p = new OnlineCheckProvider(configStore, new DefaultHttpClientFactory());
        p.api = api;

        api.setNextRedeemResponse(new JSONObject("{\"status\": \"incomplete\"," +
                "\"questions\": [" +
                "    {" +
                "        \"id\": 11," +
                "        \"type\": \"M\"," +
                "        \"question\": \"Which option?\"," +
                "        \"required\": true," +
                "        \"position\": 3," +
                "        \"options\": [{\"id\": 3, \"answer\": \"A\"}, {\"id\": 5, \"answer\": \"B\"}]" +
                "    }" +
                "]," +
                "\"data\": {" +
                "    \"item\": \"Standard Ticket\"," +
                "    \"variation\": null," +
                "    \"attendee_name\": \"Niels Bohr\"," +
                "    \"order\": \"12345\"," +
                "    \"attention\": false" +
                "}}"));
        TicketCheckProvider.CheckResult r = p.check("foooooo");
        assertEquals(TicketCheckProvider.CheckResult.Type.ANSWERS_REQUIRED, r.getType());
        assertEquals("Standard Ticket", r.getTicket());
        assertEquals("null", r.getVariation());
        assertEquals("Niels Bohr", r.getAttendee_name());
        assertEquals(false, r.isRequireAttention());
        List<TicketCheckProvider.RequiredAnswer> requiredAnswerList = r.getRequiredAnswers();
        assertEquals(1, requiredAnswerList.size());
        TicketCheckProvider.RequiredAnswer requiredAnswer = requiredAnswerList.get(0);
        assertEquals((long) 11, (long) requiredAnswer.getQuestion().getServer_id());
        assertEquals("Which option?", requiredAnswer.getQuestion().getQuestion());
        assertEquals(true, requiredAnswer.getQuestion().isRequired());
        assertEquals((long) 3, (long) requiredAnswer.getQuestion().getPosition());
        List<QuestionOption> options = requiredAnswer.getQuestion().getOptions();
        assertEquals(2, options.size());
        assertEquals((long) 3, (long) options.get(0).getServer_id());
        assertEquals("A", options.get(0).getValue());
        assertEquals((long) 5, (long) options.get(1).getServer_id());
        assertEquals("B", options.get(1).getValue());
    }

    @Test
    public void testSearch() throws JSONException, CheckException {
        OnlineCheckProvider p = new OnlineCheckProvider(configStore, new DefaultHttpClientFactory());
        p.api = api;

        api.setNextSearchResponse(new JSONObject("{\"status\": \"ok\"," +
                "\"results\": [{" +
                "    \"item\": \"Standard Ticket\"," +
                "    \"secret\": \"foooooo\"," +
                "    \"redeemed\": true," +
                "    \"paid\": true," +
                "    \"variation\": null," +
                "    \"attendee_name\": \"Niels Bohr\"," +
                "    \"order\": \"12345\"," +
                "    \"attention\": true" +
                "}]}"));
        List<TicketCheckProvider.SearchResult> srList = p.search("foooooo");
        assertEquals(1, srList.size());
        TicketCheckProvider.SearchResult r = srList.get(0);
        assertEquals("Standard Ticket", r.getTicket());
        assertEquals("null", r.getVariation());
        assertEquals("Niels Bohr", r.getAttendee_name());
        assertEquals("foooooo", r.getSecret());
        assertEquals(true, r.isRedeemed());
        assertEquals(true, r.isPaid());
        assertEquals(true, r.isRequireAttention());
    }

    @Test
    public void testStatusInfo() throws JSONException, CheckException {
        OnlineCheckProvider p = new OnlineCheckProvider(configStore, new DefaultHttpClientFactory());
        p.api = api;

        api.setNextStatusResponse(new JSONObject("{\"status\": \"ok\"," +
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
                "}]}"));
        TicketCheckProvider.StatusResult sr = p.status();
        assertEquals("DemoCon", sr.getEventName());
        assertEquals((long) 10, (long) sr.getTotalTickets());
        assertEquals((long) 5, (long) sr.getAlreadyScanned());
        assertEquals(1, sr.getItems().size());
        TicketCheckProvider.StatusResultItem i = sr.getItems().get(0);
        assertEquals((long) 5, (long) i.getId());
        assertEquals((long) 10, (long) i.getTotal());
        assertEquals((long) 5, (long) i.getCheckins());
        assertEquals(true, i.isAdmission());
        assertEquals(1, i.getVariations().size());
        TicketCheckProvider.StatusResultItemVariation v = i.getVariations().get(0);
        assertEquals((long) 1, (long) v.getId());
        assertEquals((long) 10, (long) v.getTotal());
        assertEquals((long) 5, (long) v.getCheckins());
    }
}
