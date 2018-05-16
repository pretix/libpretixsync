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
                downloadData();
                configStore.setLastDownload(System.currentTimeMillis());
            }

            configStore.setLastSync(System.currentTimeMillis());
            configStore.setLastFailedSync(0);
        } catch (SyncException e) {
            configStore.setLastFailedSync(System.currentTimeMillis());
            configStore.setLastFailedSyncMsg(e.getMessage());
        }
    }

    protected void downloadData() throws SyncException {
        sentry.addBreadcrumb("sync.queue", "Start download");

        try {
            (new ItemCategorySyncAdapter(dataStore, api)).download();
        } catch (JSONException e) {
            e.printStackTrace();
            throw new SyncException("Unknown server response");
        } catch (ApiException e) {
            sentry.addBreadcrumb("sync.tickets", "API Error: " + e.getMessage());
            throw new SyncException(e.getMessage());
        }
    }

    protected void uploadTicketData() throws SyncException {
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

                JSONObject response = api.redeem(qci.getSecret(), qci.getDatetime(), true, qci.getNonce(), answers, false);
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
}
