package eu.pretix.libpretixsync.sync;

import eu.pretix.libpretixsync.DummySentryImplementation;
import eu.pretix.libpretixsync.check.QuestionType;
import eu.pretix.libpretixsync.db.*;
import eu.pretix.libpretixsync.test.FakeConfigStore;
import eu.pretix.libpretixsync.test.FakePretixApi;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SyncManagerTicketDownloadTest extends BaseDatabaseTest {
    private FakeConfigStore configStore;
    private FakePretixApi api;

    @Before
    public void setUpFakes() {
        configStore = new FakeConfigStore();
        api = new FakePretixApi();
    }

    @Test
    public void testInitialDownload() throws SyncException, JSONException {
        SyncManager sm = new SyncManager(
                configStore, api, new DummySentryImplementation(),
                dataStore, 1000, 10000
        );
        api.setNextDownloadResponse(new JSONObject("{\"version\": 4, \"questions\": [], " +
                "\"results\": [{\n" +
                "      \"secret\": \"az9u4mymhqktrbupmwkvv6xmgds5dk3\",\n" +
                "      \"order\": \"ABCE6\",\n" +
                "      \"item\": \"Standard ticket\",\n" +
                "      \"item_id\": 9,\n" +
                "      \"variation\": null,\n" +
                "      \"variation_id\": null,\n" +
                "      \"attendee_name\": \"Peter Higgs\",\n" +
                "      \"redeemed\": false,\n" +
                "      \"attention\": false,\n" +
                "      \"paid\": true}]}"));

        sm.downloadTicketAndItemData();
        List<Ticket> ticketList = dataStore.select(Ticket.class).get().toList();
        assertEquals(1, ticketList.size());

        Ticket ticket = ticketList.get(0);
        assertEquals("az9u4mymhqktrbupmwkvv6xmgds5dk3", ticket.getSecret());
        assertEquals("ABCE6", ticket.getOrder());
        assertEquals("Standard ticket", ticket.getItem());
        assertEquals((long) 9, (long) ticket.getItem_id());
        assertEquals("null", ticket.getVariation());
        assertEquals((long) 0, (long) ticket.getVariation_id());
        assertEquals("Peter Higgs", ticket.getAttendee_name());
        assertEquals(false, ticket.isRedeemed());
        assertEquals(false, ticket.isRequire_attention());
        assertEquals(true, ticket.isPaid());
    }

    @Test
    public void testUpdatedData() throws SyncException, JSONException {
        SyncManager sm = new SyncManager(
                configStore, api, new DummySentryImplementation(),
                dataStore, 1000, 10000
        );
        api.setNextDownloadResponse(new JSONObject("{\"version\": 4, \"questions\": [], " +
                "\"results\": [" +
                "{\n" +
                "      \"secret\": \"az9u4mymhqktrbupmwkvv6xmgds5dk3\",\n" +
                "      \"order\": \"ABCE6\",\n" +
                "      \"item\": \"Standard ticket\",\n" +
                "      \"item_id\": 9,\n" +
                "      \"variation\": null,\n" +
                "      \"variation_id\": null,\n" +
                "      \"attendee_name\": \"Peter Higgs\",\n" +
                "      \"redeemed\": true,\n" +
                "      \"attention\": false,\n" +
                "      \"paid\": true}," +
                "{\n" +
                "      \"secret\": \"uZ2paUBXQmkuREWUzpbkbrqV797PWLZ5\",\n" +
                "      \"order\": \"ZYDAS\",\n" +
                "      \"item\": \"Standard ticket\",\n" +
                "      \"item_id\": 9,\n" +
                "      \"variation\": null,\n" +
                "      \"variation_id\": null,\n" +
                "      \"attendee_name\": \"Richard Feynman\",\n" +
                "      \"redeemed\": false,\n" +
                "      \"attention\": false,\n" +
                "      \"paid\": true}" +
                "]}"));

        sm.downloadTicketAndItemData();

        List<Ticket> ticketList = dataStore.select(Ticket.class).get().toList();
        assertEquals(2, ticketList.size());

        api.setNextDownloadResponse(new JSONObject("{\"version\": 4, \"questions\": [], " +
                "\"results\": [" +
                "{\n" +
                "      \"secret\": \"az9u4mymhqktrbupmwkvv6xmgds5dk3\",\n" +
                "      \"order\": \"ABCE6\",\n" +
                "      \"item\": \"Standard ticket\",\n" +
                "      \"item_id\": 9,\n" +
                "      \"variation\": null,\n" +
                "      \"variation_id\": null,\n" +
                "      \"attendee_name\": \"Peter Higgs\",\n" +
                "      \"redeemed\": true,\n" +
                "      \"attention\": false,\n" +
                "      \"paid\": true}," +
                "{\n" +
                "      \"secret\": \"K9bhe3bOvC3yP4oLa8V89OhlqhhbKDOm\",\n" +
                "      \"order\": \"12345\",\n" +
                "      \"item\": \"VIP ticket\",\n" +
                "      \"item_id\": 10,\n" +
                "      \"variation\": null,\n" +
                "      \"variation_id\": null,\n" +
                "      \"attendee_name\": \"Albert Einstein\",\n" +
                "      \"redeemed\": false,\n" +
                "      \"attention\": true,\n" +
                "      \"paid\": false}" +
                "]}"));
        sm.downloadTicketAndItemData();

        ticketList = dataStore.select(Ticket.class).orderBy(Ticket.ITEM_ID).get().toList();
        assertEquals(2, ticketList.size());

        Ticket ticket = ticketList.get(0);
        assertEquals("az9u4mymhqktrbupmwkvv6xmgds5dk3", ticket.getSecret());
        assertEquals("ABCE6", ticket.getOrder());
        assertEquals("Standard ticket", ticket.getItem());
        assertEquals((long) 9, (long) ticket.getItem_id());
        assertEquals("null", ticket.getVariation());
        assertEquals((long) 0, (long) ticket.getVariation_id());
        assertEquals("Peter Higgs", ticket.getAttendee_name());
        assertEquals(true, ticket.isRedeemed());
        assertEquals(false, ticket.isRequire_attention());
        assertEquals(true, ticket.isPaid());

        ticket = ticketList.get(1);
        assertEquals("K9bhe3bOvC3yP4oLa8V89OhlqhhbKDOm", ticket.getSecret());
        assertEquals("12345", ticket.getOrder());
        assertEquals("VIP ticket", ticket.getItem());
        assertEquals((long) 10, (long) ticket.getItem_id());
        assertEquals("null", ticket.getVariation());
        assertEquals((long) 0, (long) ticket.getVariation_id());
        assertEquals("Albert Einstein", ticket.getAttendee_name());
        assertEquals(false, ticket.isRedeemed());
        assertEquals(true, ticket.isRequire_attention());
        assertEquals(false, ticket.isPaid());
    }
}
