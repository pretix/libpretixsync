package eu.pretix.libpretixsync.sync;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import eu.pretix.libpretixsync.SentryInterface;
import eu.pretix.libpretixsync.api.ApiException;
import eu.pretix.libpretixsync.api.PretixApi;
import eu.pretix.libpretixsync.check.TicketCheckProvider;
import eu.pretix.libpretixsync.config.ConfigStore;
import eu.pretix.libpretixsync.db.Closing;
import eu.pretix.libpretixsync.db.Question;
import eu.pretix.libpretixsync.db.QueuedCheckIn;
import eu.pretix.libpretixsync.db.Receipt;
import eu.pretix.libpretixsync.db.ReceiptLine;
import io.requery.BlockingEntityStore;
import io.requery.Persistable;

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

    public void sync(boolean force) {
        if (!configStore.isConfigured()) {
            return;
        }

        if (!force && (System.currentTimeMillis() - configStore.getLastSync()) < upload_interval) {
            return;
        }
        if (!force && (System.currentTimeMillis() - configStore.getLastFailedSync()) < 30000) {
            return;
        }

        try {
            uploadTicketData();
            uploadReceipts();
            uploadClosings();

            if (force || (System.currentTimeMillis() - configStore.getLastDownload()) > download_interval) {
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
            (new ItemCategorySyncAdapter(dataStore, configStore.getEventSlug(), api)).download();
            (new ItemSyncAdapter(dataStore, configStore.getEventSlug(), api)).download();
            (new QuestionSyncAdapter(dataStore, configStore.getEventSlug(), api)).download();
            (new QuotaSyncAdapter(dataStore, configStore.getEventSlug(), api)).download();
            (new TaxRuleSyncAdapter(dataStore, configStore.getEventSlug(), api)).download();
            (new OrderSyncAdapter(dataStore, configStore.getEventSlug(), api)).download();
        } catch (JSONException e) {
            e.printStackTrace();
            throw new SyncException("Unknown server response");
        } catch (ApiException e) {
            sentry.addBreadcrumb("sync.tickets", "API Error: " + e.getMessage());
            throw new SyncException(e.getMessage());
        }
    }

    protected void uploadReceipts() throws SyncException {
        sentry.addBreadcrumb("sync.queue", "Start receipt upload");

        List<Receipt> receipts = dataStore.select(Receipt.class)
                .where(Receipt.OPEN.eq(false))
                .and(Receipt.SERVER_ID.isNull())
                .get().toList();

        try {
            for (Receipt receipt : receipts) {
                JSONObject data = receipt.toJSON();
                JSONArray lines = new JSONArray();
                for (ReceiptLine line : receipt.getLines()) {
                    lines.put(line.toJSON());
                }
                data.put("lines", lines);
                PretixApi.ApiResponse response = api.postResource(
                        api.organizerResourceUrl("posdevices/" + configStore.getPosId() + "/receipts"),
                        data
                );
                if (response.getResponse().code() == 201) {
                    receipt.setServer_id(response.getData().getLong("receipt_id"));
                    dataStore.update(receipt);
                } else {
                    throw new SyncException(response.getData().toString());
                }
            }
        } catch (JSONException e) {
            sentry.captureException(e);
            throw new SyncException("Unknown server response");
        } catch (ApiException e) {
            sentry.addBreadcrumb("sync.queue", "API Error: " + e.getMessage());
            throw new SyncException(e.getMessage());
        }

        sentry.addBreadcrumb("sync.queue", "Receipt upload complete");
    }

    protected void uploadClosings() throws SyncException {
        sentry.addBreadcrumb("sync.queue", "Start closings upload");

        List<Closing> closings = dataStore.select(Closing.class)
                .where(Closing.OPEN.eq(false))
                .and(Closing.SERVER_ID.isNull())
                .get().toList();

        try {
            for (Closing closing : closings) {
                PretixApi.ApiResponse response = api.postResource(
                        api.organizerResourceUrl("posdevices/" + configStore.getPosId() + "/closings"),
                        closing.toJSON()
                );
                if (response.getResponse().code() == 201) {
                    closing.setServer_id(response.getData().getLong("closing_id"));
                    dataStore.update(closing);
                } else {
                    throw new SyncException(response.getData().toString());
                }
            }
        } catch (JSONException e) {
            sentry.captureException(e);
            throw new SyncException("Unknown server response");
        } catch (ApiException e) {
            sentry.addBreadcrumb("sync.queue", "API Error: " + e.getMessage());
            throw new SyncException(e.getMessage());
        }

        sentry.addBreadcrumb("sync.queue", "Closings upload complete");
    }

    protected void uploadTicketData() throws SyncException {
        sentry.addBreadcrumb("sync.queue", "Start check-in upload");

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
        sentry.addBreadcrumb("sync.queue", "Check-in upload complete");
    }
}
