package eu.pretix.libpretixsync.sync;

import eu.pretix.libpretixsync.SentryInterface;
import eu.pretix.libpretixsync.api.ApiException;
import eu.pretix.libpretixsync.api.PretixApi;
import eu.pretix.libpretixsync.check.QuestionType;
import eu.pretix.libpretixsync.check.TicketCheckProvider;
import eu.pretix.libpretixsync.config.ConfigStore;
import eu.pretix.libpretixsync.db.*;
import io.requery.BlockingEntityStore;
import io.requery.Persistable;
import io.requery.util.CloseableIterator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;

public class SyncManager {
    private SentryInterface sentry;
    private PretixApi api;
    private ConfigStore configStore;
    private long upload_interval;
    private long download_interval;
    private BlockingEntityStore<Persistable> dataStore;

    public SyncManager(ConfigStore configStore, PretixApi api, SentryInterface sentry, BlockingEntityStore<Persistable> dataStore, long upload_interval, long download_interval) {
        this.configStore = configStore;
        this.api = api;
        this.sentry = sentry;
        this.upload_interval = upload_interval;
        this.download_interval = download_interval;
        this.dataStore = dataStore;
    }

    public void sync() {
        if (!configStore.isConfigured()) {
            return;
        }

        if ((System.currentTimeMillis() - configStore.getLastSync()) < upload_interval) {
            return;
        }
        if ((System.currentTimeMillis() - configStore.getLastFailedSync()) < 30000) {
            return;
        }

        try {
            uploadTicketData();

            if ((System.currentTimeMillis() - configStore.getLastDownload()) > download_interval) {
                downloadTicketAndItemData();
                configStore.setLastDownload(System.currentTimeMillis());
            }

            configStore.setLastSync(System.currentTimeMillis());
            configStore.setLastFailedSync(0);
        } catch (SyncException e) {
            configStore.setLastFailedSync(System.currentTimeMillis());
            configStore.setLastFailedSyncMsg(e.getMessage());
        }
    }

    private void uploadTicketData() throws SyncException {
        sentry.addBreadcrumb("sync.queue", "Start upload");

        List<QueuedCheckIn> queued = dataStore.select(QueuedCheckIn.class).get().toList();

        try {
            for (QueuedCheckIn qci : queued) {
                List<TicketCheckProvider.Answer> answers = new ArrayList<>();
                try {
                    JSONArray ja = new JSONArray(qci.getAnswers());
                    for (int i = 0; i < ja.length(); i++) {
                        JSONObject jo = ja.getJSONObject(i);
                        Question q = new Question();
                        q.setServer_id(jo.getLong("question"));
                        answers.add(new TicketCheckProvider.Answer(q, jo.getString("answer")));
                    }
                } catch (JSONException e) {
                }

                JSONObject response = api.redeem(qci.getSecret(), qci.getDatetime(), true, qci.getNonce(), answers);
                String status = response.getString("status");
                if ("ok".equals(status)) {
                    dataStore.delete(qci);
                } else {
                    String reason = response.optString("reason");
                    if ("already_redeemed".equals(reason)) {
                        // Well, we can't really do something about this.
                        dataStore.delete(qci);
                    } // Else: Retry later
                }
            }
        } catch (JSONException e) {
            sentry.captureException(e);
            throw new SyncException("Unknown server response");
        } catch (ApiException e) {
            sentry.addBreadcrumb("sync.tickets", "API Error: " + e.getMessage());
            throw new SyncException(e.getMessage());
        }
        sentry.addBreadcrumb("sync.queue", "Upload complete");
    }

    private static boolean long_changed(Long newint, Long oldint) {
        return (newint != null && oldint == null)
                || (newint == null && oldint != null)
                || (newint != null && oldint != null && !newint.equals(oldint));
    }

    private static boolean string_changed(String newstring, String oldstring) {
        return (newstring != null && oldstring == null)
                || (newstring == null && oldstring != null)
                || (newstring != null && oldstring != null && !newstring.equals(oldstring));
    }

    private void downloadTicketAndItemData() throws SyncException {
        sentry.addBreadcrumb("sync.tickets", "Start download");

        // Download metadata
        JSONObject response;
        try {
            response = api.status();
        } catch (ApiException e) {
            sentry.addBreadcrumb("sync.tickets", "API Error: " + e.getMessage());
            throw new SyncException(e.getMessage());
        }
        configStore.setLastStatusData(response.toString());

        // Download objects from server
        try {
            response = api.download();
        } catch (ApiException e) {
            sentry.addBreadcrumb("sync.tickets", "API Error: " + e.getMessage());
            throw new SyncException(e.getMessage());
        }

        parseItemData(response);
        parseTicketData(response);

        sentry.addBreadcrumb("sync.tickets", "Download complete");
    }

    private void parseItemData(JSONObject response) throws SyncException {
        System.out.println(response);

        if (!response.has("questions")) {
            return;
        }

        // Index all known items and questions
        Map<Long, Item> knownItems = new HashMap<>();
        Set<Long> seenItems = new HashSet<>();  // Store items that we've seen anywhere in the operation
        CloseableIterator<Item> items = dataStore.select(Item.class).get().iterator();
        try {
            while (items.hasNext()) {
                Item i = items.next();
                knownItems.put(i.getServer_id(), i);
            }
        } finally {
            items.close();
        }

        Map<Long, Question> knownQuestions = new HashMap<>();
        CloseableIterator<Question> questions = dataStore.select(Question.class).get().iterator();
        try {
            while (questions.hasNext()) {
                Question q = questions.next();
                knownQuestions.put(q.getServer_id(), q);
            }
        } finally {
            questions.close();
        }

        try {
            // Insert or update
            for (int i = 0; i < response.getJSONArray("questions").length(); i++) {
                JSONObject res = response.getJSONArray("questions").getJSONObject(i);
                // Get or create question
                Question question;
                boolean created = false;
                if (!knownQuestions.containsKey(res.getLong("id"))) {
                    question = new Question();
                    question.setServer_id(res.getLong("id"));
                    created = true;
                } else {
                    question = knownQuestions.get(res.getLong("id"));
                }

                // Update question properties
                if (string_changed(res.getString("type"), question.getType() != null ? question.getType().toString() : "")) {
                    question.setType(QuestionType.valueOf(res.getString("type")));
                }
                if (string_changed(res.getString("question"), question.getQuestion())) {
                    question.setQuestion(res.getString("question"));
                }
                if (res.optBoolean("required", false) != question.isRequired()) {
                    question.setRequired(res.optBoolean("required", false));
                }
                if (long_changed(res.optLong("position"), question.getPosition())) {
                    question.setPosition(res.optLong("position", 0));
                }

                // Save to database now, so we can use relations
                if (created) {
                    dataStore.insert(question);
                }

                // Items
                Map<Long, Item> foundItems = new HashMap<>();
                if (!created) {
                    for (Item item : question.getItems()) {
                        foundItems.put(item.getServer_id(), item);
                    }
                }
                for (int j = 0; j < res.getJSONArray("items").length(); j++) {
                    long item_id = res.getJSONArray("items").getLong(j);

                    if (foundItems.containsKey(item_id)) {
                        foundItems.remove(item_id);
                    } else {
                        if (knownItems.containsKey(item_id)) {
                            question.getItems().add(knownItems.get(item_id));
                        } else {
                            Item item = new Item();
                            item.setServer_id(item_id);
                            dataStore.insert(item);
                            question.getItems().add(item);
                            knownItems.put(item_id, item);
                        }
                    }
                    seenItems.add(item_id);
                }
                for (Long key : foundItems.keySet()) {
                    question.getItems().remove(foundItems.get(key));
                }

                // Options
                Map<Long, QuestionOption> foundOptions = new HashMap<>();
                if (!created) {
                    for (QuestionOption opt : question.getOptions()) {
                        foundOptions.put(opt.getServer_id(), opt);
                    }
                }
                for (int j = 0; j < res.getJSONArray("options").length(); j++) {
                    JSONObject opt_res = res.getJSONArray("options").getJSONObject(j);
                    QuestionOption option;
                    boolean opt_created = false;
                    long opt_id = opt_res.getLong("id");

                    if (foundOptions.containsKey(opt_id)) {
                        option = foundOptions.get(opt_id);
                        foundOptions.remove(opt_id);
                    } else {
                        option = new QuestionOption();
                        option.setQuestion(question);
                        option.setServer_id(opt_id);
                        opt_created = true;
                    }
                    if (string_changed(opt_res.getString("answer"), option.getValue())) {
                        option.setValue(opt_res.getString("answer"));
                    }
                    if (opt_created) {
                        dataStore.insert(option);
                    } else {
                        dataStore.update(option);
                    }
                }
                for (Long key : foundOptions.keySet()) {
                    dataStore.delete(foundOptions.get(key));
                }

                dataStore.update(question);
                // Remove from list so it doesn't get deleted later
                knownQuestions.remove(question.getServer_id());
            }
        } catch (JSONException e) {
            sentry.captureException(e);
            throw new SyncException("Unknown server response");
        }

        // Delete all items that we haven't seen anywhere
        for (Long key : knownItems.keySet()) {
            if (!seenItems.contains(key))
                dataStore.delete(knownItems.get(key));
        }
        // Delete all questions that we haven't seen anywhere
        for (Long key : knownQuestions.keySet()) {
            dataStore.delete(knownQuestions.get(key));
        }
    }

    private void parseTicketData(JSONObject response) throws SyncException {

        // Index all known objects
        Map<String, Ticket> known = new HashMap<>();
        CloseableIterator<Ticket> tickets = dataStore.select(Ticket.class).get().iterator();
        try {
            while (tickets.hasNext()) {
                Ticket t = tickets.next();
                known.put(t.getSecret(), t);
            }
        } finally {
            tickets.close();
        }

        try {
            List<Ticket> inserts = new ArrayList<>();
            // Insert or update
            for (int i = 0; i < response.getJSONArray("results").length(); i++) {
                JSONObject res = response.getJSONArray("results").getJSONObject(i);

                Ticket ticket;
                boolean created = false;
                if (!known.containsKey(res.getString("secret"))) {
                    ticket = new Ticket();
                    created = true;
                } else {
                    ticket = known.get(res.getString("secret"));
                }

                if (string_changed(res.getString("attendee_name"), ticket.getAttendee_name())) {
                    ticket.setAttendee_name(res.getString("attendee_name"));
                }
                if (string_changed(res.getString("item"), ticket.getItem())) {
                    ticket.setItem(res.getString("item"));
                }
                if (long_changed(res.optLong("item_id"), ticket.getItem_id())) {
                    ticket.setItem_id(res.optLong("item_id", 0));
                }
                if (string_changed(res.getString("variation"), ticket.getVariation())) {
                    ticket.setVariation(res.getString("variation"));
                }
                if (long_changed(res.optLong("variation_id"), ticket.getVariation_id())) {
                    ticket.setVariation_id(res.optLong("variation_id", 0));
                }
                if (string_changed(res.getString("order"), ticket.getOrder())) {
                    ticket.setOrder(res.getString("order"));
                }
                if (string_changed(res.getString("secret"), ticket.getSecret())) {
                    ticket.setSecret(res.getString("secret"));
                }
                if (res.optBoolean("attention", false) != ticket.isRequire_attention()) {
                    ticket.setRequire_attention(res.optBoolean("attention", false));
                }
                if (res.getBoolean("redeemed") != ticket.isRedeemed()) {
                    ticket.setRedeemed(res.getBoolean("redeemed"));
                }
                if (res.getBoolean("paid") != ticket.isPaid()) {
                    ticket.setPaid(res.getBoolean("paid"));
                }

                if (created) {
                    inserts.add(ticket);
                } else {
                    dataStore.update(ticket);
                }
                known.remove(res.getString("secret"));
            }

            dataStore.insert(inserts);
        } catch (JSONException e) {
            sentry.captureException(e);
            throw new SyncException("Unknown server response");
        }

        // Those have been deleted online, delete them here as well
        for (String key : known.keySet()) {
            dataStore.delete(known.get(key));
        }
    }
}
