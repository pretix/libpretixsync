package eu.pretix.libpretixsync.sync;

import eu.pretix.libpretixsync.DummySentryImplementation;
import eu.pretix.libpretixsync.check.TicketCheckProvider;
import eu.pretix.libpretixsync.db.BaseDatabaseTest;
import eu.pretix.libpretixsync.db.NonceGenerator;
import eu.pretix.libpretixsync.db.QueuedCheckIn;
import eu.pretix.libpretixsync.db.Ticket;
import eu.pretix.libpretixsync.test.FakeConfigStore;
import eu.pretix.libpretixsync.test.FakePretixApi;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class SyncManagerCheckInUploadTest extends BaseDatabaseTest {
    private FakeConfigStore configStore;
    private FakePretixApi api;

    @Before
    public void setUpFakes() {
        configStore = new FakeConfigStore();
        api = new FakePretixApi();
    }

    @Test
    public void testUpload() throws SyncException, JSONException {
        SyncManager sm = new SyncManager(
                configStore, api, new DummySentryImplementation(),
                dataStore, 1000, 10000
        );

        QueuedCheckIn qci = new QueuedCheckIn();
        qci.setDatetime(new Date());
        qci.setSecret("az9u4mymhqktrbupmwkvv6xmgds5dk3");
        String nonce = NonceGenerator.nextNonce();
        qci.setNonce(nonce);
        qci.setAnswers("[{\"question\": 12, \"answer\": \"Foo\"}]");

        dataStore.insert(qci);
        api.setNextRedeemResponse(new JSONObject("{\"status\": \"ok\"}"));
        sm.uploadTicketData();

        assertEquals(api.getLastSecret(), qci.getSecret());
        List<TicketCheckProvider.Answer> answers = api.getLastAnswers();
        assertEquals(1, answers.size());
        TicketCheckProvider.Answer a = answers.get(0);
        assertEquals((long) 12, (long) a.getQuestion().getServer_id());
        assertEquals("Foo", a.getValue());
    }
}
