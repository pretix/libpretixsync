package eu.pretix.libpretixsync.sync;

import eu.pretix.libpretixsync.DummySentryImplementation;
import eu.pretix.libpretixsync.check.QuestionType;
import eu.pretix.libpretixsync.db.BaseDatabaseTest;
import eu.pretix.libpretixsync.db.Item;
import eu.pretix.libpretixsync.db.Question;
import eu.pretix.libpretixsync.db.QuestionOption;
import eu.pretix.libpretixsync.test.FakeConfigStore;
import eu.pretix.libpretixsync.test.FakePretixApi;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class SyncManagerQuestionDownloadTest extends BaseDatabaseTest {
    private FakeConfigStore configStore;
    private FakePretixApi api;

    @Before
    public void setUpFakes() {
        configStore = new FakeConfigStore();
        api = new FakePretixApi();
    }

    @Test
    public void testInitialDownloadQuestion() throws SyncException, JSONException {
        SyncManager sm = new SyncManager(
                configStore, api, new DummySentryImplementation(),
                dataStore, 1000, 10000
        );
        api.setNextDownloadResponse(new JSONObject("{\"version\": 4, \"questions\": [{\"id\": 18, \"items\": [3, 12], \"required\": false, \"position\": 0, \"options\": [], \"type\": \"N\", \"question\": \"Zahl\"}], \"results\": []}"));

        sm.downloadTicketAndItemData();
        List<Item> itemList = dataStore.select(Item.class).get().toList();
        assertEquals(2, itemList.size());

        List<Question> questionList = dataStore.select(Question.class).get().toList();
        assertEquals(1, questionList.size());
        Question question = questionList.get(0);
        assertEquals((long) question.getServer_id(), (long) 18);
        assertEquals(question.isRequired(), false);
        assertEquals((long) question.getPosition(), (long) 0);
        assertEquals(question.getOptions().size(), 0);
        assertEquals(question.getType(), QuestionType.N);
        assertEquals(question.getQuestion(), "Zahl");
        assertEquals(question.getItems().size(), 2);
        assertTrue(question.getItems().contains(itemList.get(0)));
        assertTrue(question.getItems().contains(itemList.get(1)));
    }

    @Test
    public void testInitialDownloadQuestionChoice() throws SyncException, JSONException {
        SyncManager sm = new SyncManager(
                configStore, api, new DummySentryImplementation(),
                dataStore, 1000, 10000
        );
        api.setNextDownloadResponse(new JSONObject("{\"version\": 4, \"questions\": [{\"id\": 18, \"items\": [3, 12], \"required\": false, \"position\": 0, \"options\": [{\"id\": 27, \"answer\": \"A\"}, {\"id\": 28, \"answer\": \"B\"}], \"type\": \"C\", \"question\": \"Auswahl\"}], \"results\": []}"));

        sm.downloadTicketAndItemData();
        List<Item> itemList = dataStore.select(Item.class).get().toList();
        assertEquals(2, itemList.size());

        List<Question> questionList = dataStore.select(Question.class).get().toList();
        assertEquals(1, questionList.size());
        Question question = questionList.get(0);
        assertEquals((long) question.getServer_id(), (long) 18);
        assertEquals(question.isRequired(), false);
        assertEquals((long) question.getPosition(), (long) 0);
        assertEquals(question.getType(), QuestionType.C);
        assertEquals(question.getQuestion(), "Auswahl");
        assertEquals(question.getOptions().size(), 2);
        List<QuestionOption> optionList = question.getOptions();
        Collections.sort(optionList, new Comparator<QuestionOption>() {
            @Override
            public int compare(QuestionOption o1, QuestionOption o2) {
                return o1.server_id.compareTo(o2.server_id);
            }
        });
        assertEquals((long) question.getOptions().get(0).server_id, (long) 27);
        assertEquals(question.getOptions().get(0).value, "A");
        assertEquals((long) question.getOptions().get(1).server_id, (long) 28);
        assertEquals(question.getOptions().get(1).value, "B");
    }

    @Test
    public void testQuestionAndDependenciesChanged() throws SyncException, JSONException {
        SyncManager sm = new SyncManager(
                configStore, api, new DummySentryImplementation(),
                dataStore, 1000, 10000
        );
        api.setNextDownloadResponse(new JSONObject("{\"version\": 4, \"questions\": [{\"id\": 18, \"items\": [3, 12], \"required\": false, \"position\": 0, \"options\": [{\"id\": 27, \"answer\": \"A\"}, {\"id\": 28, \"answer\": \"B\"}], \"type\": \"C\", \"question\": \"Auswahl\"}], \"results\": []}"));
        sm.downloadTicketAndItemData();

        api.setNextDownloadResponse(new JSONObject("{\"version\": 4, \"questions\": [{\"id\": 18, \"items\": [14], \"required\": true, \"position\": 0, \"options\": [{\"id\": 27, \"answer\": \"B\"}, {\"id\": 29, \"answer\": \"C\"}, {\"id\": 30, \"answer\": \"D\"}], \"type\": \"M\", \"question\": \"Mehrfachauswahl\"}], \"results\": []}"));
        sm.downloadTicketAndItemData();

        List<Item> itemList = dataStore.select(Item.class).get().toList();
        assertEquals(1, itemList.size());

        List<Question> questionList = dataStore.select(Question.class).get().toList();
        assertEquals(1, questionList.size());
        Question question = questionList.get(0);
        assertEquals((long) question.getServer_id(), (long) 18);
        assertEquals(question.isRequired(), true);
        assertEquals((long) question.getPosition(), (long) 0);
        assertEquals(question.getType(), QuestionType.M);
        assertEquals(question.getQuestion(), "Mehrfachauswahl");
        assertEquals(question.getOptions().size(), 3);
        List<QuestionOption> optionList = question.getOptions();
        Collections.sort(optionList, new Comparator<QuestionOption>() {
            @Override
            public int compare(QuestionOption o1, QuestionOption o2) {
                return o1.server_id.compareTo(o2.server_id);
            }
        });
        assertEquals((long) question.getOptions().get(0).server_id, (long) 27);
        assertEquals(question.getOptions().get(0).value, "B");
        assertEquals((long) question.getOptions().get(1).server_id, (long) 29);
        assertEquals(question.getOptions().get(1).value, "C");
        assertEquals((long) question.getOptions().get(2).server_id, (long) 30);
        assertEquals(question.getOptions().get(2).value, "D");
    }

    @Test
    public void testQuestionRemoved() throws SyncException, JSONException {
        SyncManager sm = new SyncManager(
                configStore, api, new DummySentryImplementation(),
                dataStore, 1000, 10000
        );
        api.setNextDownloadResponse(new JSONObject("{\"version\": 4, \"questions\": [{\"id\": 18, \"items\": [3, 12], \"required\": false, \"position\": 0, \"options\": [{\"id\": 27, \"answer\": \"A\"}, {\"id\": 28, \"answer\": \"B\"}], \"type\": \"C\", \"question\": \"Auswahl\"}], \"results\": []}"));
        sm.downloadTicketAndItemData();

        List<Item> itemList = dataStore.select(Item.class).get().toList();
        assertEquals(2, itemList.size());

        List<Question> questionList = dataStore.select(Question.class).get().toList();
        assertEquals(1, questionList.size());

        api.setNextDownloadResponse(new JSONObject("{\"version\": 4, \"questions\": [], \"results\": []}"));
        sm.downloadTicketAndItemData();

        itemList = dataStore.select(Item.class).get().toList();
        assertEquals(0, itemList.size());

        questionList = dataStore.select(Question.class).get().toList();
        assertEquals(0, questionList.size());

        List<QuestionOption> optionList = dataStore.select(QuestionOption.class).get().toList();
        assertEquals(0, optionList.size());
    }
}
