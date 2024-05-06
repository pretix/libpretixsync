package eu.pretix.libpretixsync.sync;

import eu.pretix.libpretixsync.api.*;
import eu.pretix.libpretixsync.db.ReusableMedium;
import eu.pretix.libpretixsync.sqldelight.SyncDatabase;
import eu.pretix.libpretixsync.utils.JSONUtils;
import io.requery.sql.StatementExecutionException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import eu.pretix.libpretixsync.SentryInterface;
import eu.pretix.libpretixsync.config.ConfigStore;
import eu.pretix.libpretixsync.db.Answer;
import eu.pretix.libpretixsync.db.CheckIn;
import eu.pretix.libpretixsync.db.Closing;
import eu.pretix.libpretixsync.db.Order;
import eu.pretix.libpretixsync.db.OrderPosition;
import eu.pretix.libpretixsync.db.Question;
import eu.pretix.libpretixsync.db.QueuedCall;
import eu.pretix.libpretixsync.db.QueuedCheckIn;
import eu.pretix.libpretixsync.db.QueuedOrder;
import eu.pretix.libpretixsync.db.Receipt;
import eu.pretix.libpretixsync.db.ReceiptLine;
import eu.pretix.libpretixsync.db.ReceiptPayment;
import eu.pretix.libpretixsync.db.ResourceSyncStatus;
import io.requery.BlockingEntityStore;
import io.requery.Persistable;

public class SyncManager {
    public enum Profile {
        PRETIXPOS, PRETIXSCAN, PRETIXSCAN_ONLINE
    }

    protected SentryInterface sentry;
    protected PretixApi api;
    protected ConfigStore configStore;
    protected long upload_interval;
    protected long download_interval;
    protected BlockingEntityStore<Persistable> dataStore;
    protected SyncDatabase db;
    protected FileStorage fileStorage;
    protected Profile profile;
    protected boolean with_pdf_data;
    protected CanceledState canceled;
    protected int app_version;
    protected JSONObject app_info;
    protected String hardware_brand;
    protected String hardware_model;
    protected String os_name;
    protected String os_version;
    protected String software_brand;
    protected String software_version;
    protected String rsa_pubkey;
    protected String salesChannel;
    protected CheckConnectivityFeedback connectivityFeedback;

    public class CanceledState {
        private boolean canceled = false;

        public boolean isCanceled() {
            return canceled;
        }

        public void setCanceled(boolean canceled) {
            this.canceled = canceled;
        }
    }

    public interface ProgressFeedback {
        public void postFeedback(String current_action);
    }

    public interface CheckConnectivityFeedback {
        void recordError();

        void recordSuccess(Long durationInMillis);
    }

    public static class EventSwitchRequested extends Exception {
        public String eventSlug;
        public String eventName;
        public Long subeventId;
        public Long checkinlistId;

        public EventSwitchRequested(String eventSlug, String eventName, Long subeventId, Long checkinlistId) {
            this.eventSlug = eventSlug;
            this.eventName = eventName;
            this.subeventId = subeventId;
            this.checkinlistId = checkinlistId;
        }
    }

    public SyncManager(
            ConfigStore configStore,
            PretixApi api,
            SentryInterface sentry,
            BlockingEntityStore<Persistable> dataStore,
            SyncDatabase db,
            FileStorage fileStorage,
            long upload_interval,
            long download_interval,
            Profile profile,
            boolean with_pdf_data,
            int app_version,
            JSONObject app_info,
            String hardware_brand,
            String hardware_model,
            String os_name,
            String os_version,
            String software_brand,
            String software_version,
            String rsa_pubkey,
            String salesChannel,
            CheckConnectivityFeedback connectivityFeedback
    ) {
        this.configStore = configStore;
        this.api = api;
        this.sentry = sentry;
        this.upload_interval = upload_interval;
        this.download_interval = download_interval;
        this.dataStore = dataStore;
        this.db = db;
        this.fileStorage = fileStorage;
        this.profile = profile;
        this.with_pdf_data = with_pdf_data;
        this.canceled = new CanceledState();
        this.app_version = app_version;
        this.app_info = app_info;
        this.hardware_brand = hardware_brand;
        this.hardware_model = hardware_model;
        this.os_name = os_name;
        this.os_version = os_version;
        this.software_brand = software_brand;
        this.software_version = software_version;
        this.rsa_pubkey = rsa_pubkey;
        this.salesChannel = salesChannel;
        this.connectivityFeedback = connectivityFeedback;
    }

    public SyncResult sync(boolean force) throws EventSwitchRequested {
        return sync(force, null);
    }

    /**
     * Synchronize data with the pretix server
     *
     * @param force Force a new sync
     * @return A SyncResult describing the results of the synchronization
     */
    public SyncResult sync(boolean force, SyncManager.ProgressFeedback feedback) throws EventSwitchRequested {
        if (!configStore.isConfigured()) {
            return new SyncResult(false, false, null);
        }

        if (!force && (System.currentTimeMillis() - configStore.getLastSync()) < upload_interval) {
            return new SyncResult(false, false, null);
        }
        if (!force && (System.currentTimeMillis() - configStore.getLastFailedSync()) < 30000) {
            return new SyncResult(false, false, null);
        }

        bumpKnownVersion();

        canceled.setCanceled(false);
        boolean download = force || (System.currentTimeMillis() - configStore.getLastDownload()) > download_interval;
        try {
            if (configStore.getAutoSwitchRequested() && configStore.getSynchronizedEvents().size() == 1) {
                if (feedback != null) {
                    feedback.postFeedback("Checking for other event…");
                }
                checkEventSelection(configStore.getSelectedCheckinListForEvent(configStore.getSynchronizedEvents().get(0)));
            }
            upload(feedback);

            if (download) {
                if (feedback != null) {
                    feedback.postFeedback("Downloading data…");
                }
                downloadData(feedback, false, null, 0L);
                configStore.setLastDownload(System.currentTimeMillis());
            }

            if (feedback != null) {
                feedback.postFeedback("Finishing touches…");
            }
            configStore.setLastSync(System.currentTimeMillis());
            configStore.setLastFailedSync(0);
            if (feedback != null) {
                feedback.postFeedback("Sync completed.");
            }
        } catch (StatementExecutionException e) {
            if (e.getCause() != null && e.getCause().getMessage().contains("SQLITE_BUSY")) {
                configStore.setLastFailedSync(System.currentTimeMillis());
                configStore.setLastFailedSyncMsg("Local database was locked");
                return new SyncResult(true, download, e);
            } else {
                throw e;
            }
        } catch (SyncException e) {
            configStore.setLastFailedSync(System.currentTimeMillis());
            configStore.setLastFailedSyncMsg(e.getMessage());
            return new SyncResult(true, download, e);
        }
        return new SyncResult(true, download, null);
    }

    /**
     * Like sync(), but without order data and with setting the sync state to "unsynced"
     */
    public SyncResult syncMinimalEventSet(String overrideEventSlug, long overrideSubeventId, ProgressFeedback feedback) {
        bumpKnownVersion();
        try {
            upload(feedback);
            downloadData(feedback, true, overrideEventSlug, overrideSubeventId);
            configStore.setLastDownload(0);
            configStore.setLastSync(0);
        } catch (SyncException e) {
            configStore.setLastFailedSync(System.currentTimeMillis());
            configStore.setLastFailedSyncMsg(e.getMessage());
            return new SyncResult(true, true, e);
        }
        return new SyncResult(true, true, null);
    }

    public SyncResult syncMinimalEventSet(SyncManager.ProgressFeedback feedback) {
        return syncMinimalEventSet(null, 0L, feedback);
    }

    private void checkEventSelection(Long listId) throws EventSwitchRequested {
        try {
            if (configStore.getSynchronizedEvents().size() != 1) {
                // Only supported if exactly one event is selected
                return;
            }
            String configEventSlug = configStore.getSynchronizedEvents().get(0);
            Long configSubeventId = configStore.getSelectedSubeventForEvent(configEventSlug);
            String query = "current_event=" + configEventSlug;
            if (configSubeventId != null && configSubeventId > 0) {
                query += "&current_subevent=" + configSubeventId;
            }
            if (listId != null && listId > 0) {
                query += "&current_checkinlist=" + listId;
            }
            PretixApi.ApiResponse resp = api.fetchResource(
                    api.apiURL("device/eventselection?" + query)
            );
            if (resp.getResponse().code() == 200) {
                String eventSlug = resp.getData().getJSONObject("event").getString("slug");
                Long subeventId = resp.getData().isNull("subevent") ? 0 : resp.getData().optLong("subevent", 0);
                Long checkinlistId = resp.getData().isNull("checkinlist") ? 0 : resp.getData().optLong("checkinlist", 0);
                if (!eventSlug.equals(configEventSlug) || !subeventId.equals(configSubeventId) || !checkinlistId.equals(listId)) {
                    throw new EventSwitchRequested(eventSlug, resp.getData().getJSONObject("event").getString("name"), subeventId, checkinlistId);
                }
            }
        } catch (ResourceNotModified e) {
            // Current event is fine
        } catch (NotFoundApiException e) {
            // Either there are no available events, or the pretix version is too old. Either way,
            // we don't care.
        } catch (ApiException | JSONException e) {
            configStore.setLastFailedSync(System.currentTimeMillis());
            configStore.setLastFailedSyncMsg(e.getMessage());
        }
    }

    private void bumpKnownVersion() {
        try {
            JSONObject newInfo = app_info != null ? app_info : configStore.getDeviceKnownInfo();
            if (app_version != configStore.getDeviceKnownVersion() || !JSONUtils.similar(newInfo, configStore.getDeviceKnownInfo())) {
                JSONObject apiBody = new JSONObject();
                apiBody.put("hardware_brand", hardware_brand);
                apiBody.put("hardware_model", hardware_model);
                apiBody.put("os_name", os_name);
                apiBody.put("os_version", os_version);
                apiBody.put("software_brand", software_brand);
                apiBody.put("software_version", software_version);
                if (rsa_pubkey != null) {
                    apiBody.put("rsa_pubkey", rsa_pubkey);
                }
                if (newInfo != null) {
                    apiBody.put("info", newInfo);
                }
                PretixApi.ApiResponse resp = api.postResource(
                        api.apiURL("device/update"),
                        apiBody
                );
                configStore.setDeviceKnownVersion(app_version);
                configStore.setDeviceKnownInfo(app_info);
                configStore.setDeviceKnownName(resp.getData().getString("name"));
                String gateName = null;
                long gateID = 0;
                if (resp.getData().has("gate") && !resp.getData().isNull("gate")) {
                    gateName = resp.getData().getJSONObject("gate").getString("name");
                    gateID = resp.getData().getJSONObject("gate").getLong("id");
                }
                configStore.setDeviceKnownGateName(gateName);
                configStore.setDeviceKnownGateID(gateID);
            }
        } catch (ApiException | JSONException e) {
            configStore.setLastFailedSync(System.currentTimeMillis());
            configStore.setLastFailedSyncMsg(e.getMessage());
        }

        // This is kind of a manual migration, added in 2020-19. Remove at some late point in time
        try {
            dataStore.raw("UPDATE checkin SET listId = list WHERE (listId IS NULL OR listID = 0) AND list IS NOT NULL AND list > 0");
        } catch (Exception e) {
            // old column doesn't exist? ignore!
        }
    }

    protected void upload(ProgressFeedback feedback) throws SyncException {
        if (feedback != null) {
            feedback.postFeedback("Uploading orders…");
        }
        uploadOrders(feedback);
        if (canceled.isCanceled()) throw new SyncException("Canceled");

        if (feedback != null) {
            feedback.postFeedback("Uploading checkins…");
        }
        uploadCheckins(feedback);
        if (canceled.isCanceled()) throw new SyncException("Canceled");

        if (feedback != null) {
            feedback.postFeedback("Uploading queued calls…");
        }
        uploadQueuedCalls(feedback);
        if (canceled.isCanceled()) throw new SyncException("Canceled");

        if (feedback != null) {
            feedback.postFeedback("Uploading receipts…");
        }
        uploadReceipts(feedback);
        if (canceled.isCanceled()) throw new SyncException("Canceled");

        if (feedback != null) {
            feedback.postFeedback("Uploading closings…");
        }
        uploadClosings(feedback);
        if (canceled.isCanceled()) throw new SyncException("Canceled");
    }

    private void download(DownloadSyncAdapter adapter) throws InterruptedException, ExecutionException, ApiException, JSONException {
        adapter.setCancelState(canceled);
        adapter.download();
    }

    public void cancel() {
        canceled.setCanceled(true);
    }

    protected void fetchDeviceInfo() throws ApiException, JSONException, ResourceNotModified, ExecutionException, InterruptedException {
        try {
            PretixApi.ApiResponse vresp = api.fetchResource(api.apiURL("device/info"));
            JSONObject vdata = vresp.getData();
            configStore.setKnownPretixVersion(vdata.getJSONObject("server").getJSONObject("version").getLong("pretix_numeric"));

            configStore.setDeviceKnownName(vdata.getJSONObject("device").getString("name"));
            String gateName = null;
            long gateID = 0;
            if (vdata.getJSONObject("device").has("gate") && !vdata.getJSONObject("device").isNull("gate")) {
                gateName = vdata.getJSONObject("device").getJSONObject("gate").getString("name");
                gateID = vdata.getJSONObject("device").getJSONObject("gate").getLong("id");
            }
            configStore.setDeviceKnownGateName(gateName);
            configStore.setDeviceKnownGateID(gateID);

            if (vdata.has("medium_key_sets")) {
                MediumKeySetSyncAdapter mkssa = new MediumKeySetSyncAdapter(dataStore, fileStorage, api, configStore.getSyncCycleId(), null, vdata.getJSONArray("medium_key_sets"));
                mkssa.download();
            }

        } catch (NotFoundApiException e) {
            // pretix pre 4.13
            PretixApi.ApiResponse vresp = api.fetchResource(api.apiURL("version"));
            configStore.setKnownPretixVersion(vresp.getData().getLong("pretix_numeric"));
        }
    }


    protected void downloadData(ProgressFeedback feedback, Boolean skip_orders, String overrideEventSlug, Long overrideSubeventId) throws SyncException {
        sentry.addBreadcrumb("sync.queue", "Start download");

        try {
            try {
                fetchDeviceInfo();
            } catch (ApiException | JSONException | ResourceNotModified e) {
                // ignore
                e.printStackTrace();
            }


            if (profile == Profile.PRETIXPOS) {
                try {
                    download(new CashierSyncAdapter(db, fileStorage, api, configStore.getSyncCycleId(), feedback));
                } catch (NotFoundApiException e) {
                    // ignore, this is only supported from a later pretixpos-backend version
                }
                if (configStore.getSynchronizedEvents().size() == 0) {
                    return;
                }
            }

            download(new AllSubEventsSyncAdapter(dataStore, fileStorage, api, configStore.getSyncCycleId(), feedback));
            List<String> slugs;
            if (overrideEventSlug != null) {
                slugs = new ArrayList<>();
                slugs.add(overrideEventSlug);
            } else {
                slugs = configStore.getSynchronizedEvents();
            }
            for (String eventSlug : slugs) {
                Long subEvent = configStore.getSelectedSubeventForEvent(eventSlug);
                if (overrideSubeventId > 0L) {
                    subEvent = overrideSubeventId;
                }
                try {
                    download(new EventSyncAdapter(dataStore, eventSlug, eventSlug, api, configStore.getSyncCycleId(), feedback));
                } catch (PermissionDeniedApiException e) {
                    e.eventSlug = eventSlug;
                    throw e;
                }
                download(new ItemCategorySyncAdapter(dataStore, fileStorage, eventSlug, api, configStore.getSyncCycleId(), feedback));
                download(new ItemSyncAdapter(db, fileStorage, eventSlug, api, configStore.getSyncCycleId(), feedback));
                download(new QuestionSyncAdapter(dataStore, fileStorage, eventSlug, api, configStore.getSyncCycleId(), feedback));
                if (profile == Profile.PRETIXPOS) {
                    download(new QuotaSyncAdapter(dataStore, fileStorage, eventSlug, api, configStore.getSyncCycleId(), feedback, subEvent));
                    download(new TaxRuleSyncAdapter(dataStore, fileStorage, eventSlug, api, configStore.getSyncCycleId(), feedback));
                    download(new TicketLayoutSyncAdapter(dataStore, fileStorage, eventSlug, api, configStore.getSyncCycleId(), salesChannel, feedback));
                }
                download(new BadgeLayoutSyncAdapter(dataStore, fileStorage, eventSlug, api, configStore.getSyncCycleId(), feedback));
                download(new BadgeLayoutItemSyncAdapter(dataStore, fileStorage, eventSlug, api, configStore.getSyncCycleId(), feedback));
                download(new CheckInListSyncAdapter(dataStore, fileStorage, eventSlug, api, configStore.getSyncCycleId(), feedback, subEvent));
                if (profile == Profile.PRETIXSCAN || profile == Profile.PRETIXSCAN_ONLINE) {
                    // We don't need these on pretixPOS, so we can save some traffic
                    try {
                        download(new RevokedTicketSecretSyncAdapter(dataStore, fileStorage, eventSlug, api, configStore.getSyncCycleId(), feedback));
                    } catch (NotFoundApiException e) {
                        // ignore, this is only supported from pretix 3.12.
                    }
                    try {
                        download(new BlockedTicketSecretSyncAdapter(dataStore, fileStorage, eventSlug, api, configStore.getSyncCycleId(), feedback));
                    } catch (NotFoundApiException e) {
                        // ignore, this is only supported from pretix 4.17.
                    }
                }
                if (profile == Profile.PRETIXSCAN && !skip_orders) {
                    OrderSyncAdapter osa = new OrderSyncAdapter(dataStore, fileStorage, eventSlug, subEvent, with_pdf_data, false, api, configStore.getSyncCycleId(), feedback);
                    download(osa);
                    try {
                        download(new ReusableMediaSyncAdapter(dataStore, fileStorage, eventSlug, api, configStore.getSyncCycleId(), feedback));
                    } catch (NotFoundApiException e) {
                        // ignore, this is only supported from pretix 4.19.
                    }
                }

                try {
                    download(new SettingsSyncAdapter(dataStore, eventSlug, eventSlug, api, configStore.getSyncCycleId(), feedback));
                } catch (ApiException e) {
                    // Older pretix installations
                    // We don't need these on pretixSCAN, so we can save some traffic
                    if (profile == Profile.PRETIXPOS) {
                        download(new InvoiceSettingsSyncAdapter(dataStore, eventSlug, eventSlug, api, configStore.getSyncCycleId(), feedback));
                    }
                }
            }

            if (profile == Profile.PRETIXSCAN && !skip_orders && overrideEventSlug == null) {
                OrderCleanup oc = new OrderCleanup(dataStore, fileStorage, api, configStore.getSyncCycleId(), feedback);
                if ((System.currentTimeMillis() - configStore.getLastCleanup()) > 3600 * 1000 * 12) {
                    for (String eventSlug : configStore.getSynchronizedEvents()) {
                        oc.deleteOldSubevents(eventSlug, overrideSubeventId > 0L ? overrideSubeventId : configStore.getSelectedSubeventForEvent(eventSlug));
                    }
                    oc.deleteOldEvents(configStore.getSynchronizedEvents());
                    oc.deleteOldPdfImages();
                    configStore.setLastCleanup(System.currentTimeMillis());
                }
            } else if (profile == Profile.PRETIXSCAN_ONLINE && overrideEventSlug == null) {
                dataStore.delete(CheckIn.class).get().value();
                dataStore.delete(OrderPosition.class).get().value();
                dataStore.delete(Order.class).get().value();
                dataStore.delete(ResourceSyncStatus.class).where(ResourceSyncStatus.RESOURCE.like("order%")).get().value();
                if ((System.currentTimeMillis() - configStore.getLastCleanup()) > 3600 * 1000 * 12) {
                    OrderCleanup oc = new OrderCleanup(dataStore, fileStorage, api, configStore.getSyncCycleId(), feedback);
                    oc.deleteOldPdfImages();
                    configStore.setLastCleanup(System.currentTimeMillis());
                }
            }


        } catch (DeviceAccessRevokedException e) {
            int deleted = 0;
            deleted += dataStore.delete(CheckIn.class).get().value();
            deleted += dataStore.delete(OrderPosition.class).get().value();
            deleted += dataStore.delete(Order.class).get().value();
            deleted += dataStore.delete(ReusableMedium.class).get().value();
            deleted += dataStore.delete(ResourceSyncStatus.class).get().value();
            throw new SyncException(e.getMessage());
        } catch (JSONException e) {
            e.printStackTrace();
            throw new SyncException("Unknown server response: " + e.getMessage());
        } catch (ApiException e) {
            sentry.addBreadcrumb("sync.tickets", "API Error: " + e.getMessage());
            throw new SyncException(e.getMessage(), e);
        } catch (ExecutionException e) {
            sentry.captureException(e);
            throw new SyncException(e.getMessage());
        } catch (InterruptedException e) {
            sentry.captureException(e);
            throw new SyncException(e.getMessage());
        }
    }

    protected void uploadQueuedCalls(ProgressFeedback feedback) throws SyncException {
        sentry.addBreadcrumb("sync.queue", "Start queuedcall upload");

        List<QueuedCall> calls = dataStore.select(QueuedCall.class)
                .get().toList();
        String url = "";
        int i = 0;
        for (QueuedCall call : calls) {
            try {
                if (feedback != null && i % 10 == 0) {
                    feedback.postFeedback("Uploading queued calls (" + i + "/" + calls.size() + ") …");
                }
                i++;
                url = call.url;
                PretixApi.ApiResponse response = api.postResource(
                        call.url,
                        new JSONObject(call.body),
                        call.idempotency_key
                );
                if (response.getResponse().code() < 500) {
                    dataStore.delete(call);
                    if (response.getResponse().code() >= 400) {
                        sentry.captureException(new ApiException("Received response (" + response.getResponse().code() + ") for queued call: " + response.getData().toString()));
                        // We ignore 400s, because we can't do something about them
                    }
                } else {
                    throw new SyncException(response.getData().toString());
                }
            } catch (JSONException e) {
                sentry.captureException(e);
                throw new SyncException("Unknown server response: " + e.getMessage());
            } catch (NotFoundApiException e) {
                if (url.contains("/failed_checkins/") || url.contains("/printlog/")) {
                    // ignore this one: old pretix systems don't have it
                    dataStore.delete(call);
                } else {
                    sentry.addBreadcrumb("sync.queue", "API Error: " + e.getMessage());
                    throw new SyncException(e.getMessage());
                }
            } catch (ApiException e) {
                sentry.addBreadcrumb("sync.queue", "API Error: " + e.getMessage());
                throw new SyncException(e.getMessage());
            }
        }

        sentry.addBreadcrumb("sync.queue", "Queued call upload complete");
    }

    protected void uploadReceipts(ProgressFeedback feedback) throws SyncException {
        sentry.addBreadcrumb("sync.queue", "Start receipt upload");

        List<Receipt> receipts = dataStore.select(Receipt.class)
                .where(Receipt.OPEN.eq(false))
                .and(Receipt.SERVER_ID.isNull())
                .get().toList();

        try {
            int i = 0;
            for (Receipt receipt : receipts) {
                if (feedback != null && (i % 10) == 0) {
                    feedback.postFeedback("Uploading receipts (" + i + "/" + receipts.size() + ") …");
                }
                i++;


                JSONObject data = receipt.toJSON();
                JSONArray lines = new JSONArray();
                JSONArray payments = new JSONArray();
                for (ReceiptLine line : receipt.getLines()) {
                    // TODO: Manually add addon_to.positionid when switching to SQLDelight
                    lines.put(line.toJSON());
                }
                for (ReceiptPayment payment : receipt.getPayments()) {
                    payments.put(payment.toJSON());
                }
                data.put("lines", lines);
                data.put("payments", payments);
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
            throw new SyncException("Unknown server response: " + e.getMessage());
        } catch (ApiException e) {
            sentry.addBreadcrumb("sync.queue", "API Error: " + e.getMessage());
            throw new SyncException(e.getMessage());
        }

        sentry.addBreadcrumb("sync.queue", "Receipt upload complete");
    }

    protected void uploadOrders(ProgressFeedback feedback) throws SyncException {
        sentry.addBreadcrumb("sync.queue", "Start order upload");

        List<QueuedOrder> orders = dataStore.select(QueuedOrder.class)
                .where(QueuedOrder.ERROR.isNull())
                .and(QueuedOrder.LOCKED.eq(false))
                .get().toList();

        try {
            int i = 0;
            for (QueuedOrder qo : orders) {
                if (feedback != null && i % 10 == 0) {
                    feedback.postFeedback("Uploading orders (" + i + "/" + orders.size() + ") …");
                }
                i++;

                qo.setLocked(true);
                dataStore.update(qo, QueuedOrder.LOCKED);
                Long startedAt = System.currentTimeMillis();
                PretixApi.ApiResponse resp = api.postResource(
                        api.eventResourceUrl(qo.getEvent_slug(), "orders") + "?pdf_data=true&force=true",
                        new JSONObject(qo.getPayload()),
                        qo.getIdempotency_key()
                );
                if (resp.getResponse().code() == 201) {
                    Receipt r = qo.getReceipt();
                    r.setOrder_code(resp.getData().getString("code"));
                    dataStore.update(r, Receipt.ORDER_CODE);
                    dataStore.delete(qo);
                    (new OrderSyncAdapter(dataStore, fileStorage, qo.getEvent_slug(), null, true, true, api, configStore.getSyncCycleId(), null)).standaloneRefreshFromJSON(resp.getData());
                    if (connectivityFeedback != null) {
                        connectivityFeedback.recordSuccess(System.currentTimeMillis() - startedAt);
                    }
                } else if (resp.getResponse().code() == 400) {
                    // TODO: User feedback or log in some way?
                    qo.setError(resp.getData().toString());
                    dataStore.update(qo);
                }
            }
        } catch (JSONException e) {
            if (connectivityFeedback != null) {
                connectivityFeedback.recordError();
            }
            sentry.captureException(e);
            throw new SyncException("Unknown server response: " + e.getMessage());
        } catch (ApiException e) {
            if (connectivityFeedback != null) {
                connectivityFeedback.recordError();
            }
            sentry.addBreadcrumb("sync.queue", "API Error: " + e.getMessage());
            throw new SyncException(e.getMessage());
        }

        sentry.addBreadcrumb("sync.queue", "Receipt upload complete");
    }

    protected void uploadClosings(ProgressFeedback feedback) throws SyncException {
        sentry.addBreadcrumb("sync.queue", "Start closings upload");

        List<Closing> closings = dataStore.select(Closing.class)
                .where(Closing.OPEN.eq(false))
                .and(Closing.SERVER_ID.isNull())
                .get().toList();

        try {
            int i = 0;
            for (Closing closing : closings) {
                if (feedback != null && i % 10 == 0) {
                    feedback.postFeedback("Uploading closings (" + i + "/" + closings.size() + ") …");
                }
                i++;
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
            throw new SyncException("Unknown server response: " + e.getMessage());
        } catch (ApiException e) {
            sentry.addBreadcrumb("sync.queue", "API Error: " + e.getMessage());
            throw new SyncException(e.getMessage());
        }

        sentry.addBreadcrumb("sync.queue", "Closings upload complete");
    }

    protected void uploadCheckins(ProgressFeedback feedback) throws SyncException {
        sentry.addBreadcrumb("sync.queue", "Start check-in upload");

        List<QueuedCheckIn> queued = dataStore.select(QueuedCheckIn.class)
                .get().toList();

        try {
            int i = 0;
            for (QueuedCheckIn qci : queued) {
                if (feedback != null && i % 10 == 0) {
                    feedback.postFeedback("Uploading checkins (" + i + "/" + queued.size() + ") …");
                }
                i++;
                List<Answer> answers = new ArrayList<>();
                try {
                    JSONArray ja = new JSONArray(qci.getAnswers());
                    for (int j = 0; j < ja.length(); j++) {
                        JSONObject jo = ja.getJSONObject(j);
                        Question q = new Question();
                        q.setServer_id(jo.getLong("question"));
                        answers.add(new Answer(q, jo.getString("answer"), null));
                    }
                } catch (JSONException e) {
                }

                PretixApi.ApiResponse ar;
                Long startedAt = System.currentTimeMillis();
                String st = "barcode";
                if (qci.getSource_type() != null) {
                    st = qci.getSource_type();
                }
                if (qci.getDatetime_string() == null || qci.getDatetime_string().equals("")) {
                    // Backwards compatibility
                    ar = api.redeem(qci.getEvent_slug(), qci.getSecret(), qci.getDatetime(), true, qci.getNonce(), answers, qci.checkinListId, false, false, qci.getType(), st, null, false);
                } else {
                    ar = api.redeem(qci.getEvent_slug(), qci.getSecret(), qci.getDatetime_string(), true, qci.getNonce(), answers, qci.checkinListId, false, false, qci.getType(), st, null, false);
                }
                if (connectivityFeedback != null) {
                    connectivityFeedback.recordSuccess(System.currentTimeMillis() - startedAt);
                }
                JSONObject response = ar.getData();
                String status = response.optString("status", null);
                if ("ok".equals(status)) {
                    dataStore.delete(qci);
                } else if (ar.getResponse().code() == 404 || ar.getResponse().code() == 400) {
                    // There's no point in re-trying a 404 or 400 since it won't change on later uploads.
                    // Modern pretix versions already log this case and handle it if possible, nothing
                    // we can do here.
                    dataStore.delete(qci);
                } // Else: will be retried later
            }
        } catch (JSONException e) {
            if (connectivityFeedback != null) {
                connectivityFeedback.recordError();
            }
            sentry.captureException(e);
            throw new SyncException("Unknown server response: " + e.getMessage());
        } catch (NotFoundApiException e) {
            // Ticket secret no longer exists, too bad :\
        } catch (ApiException e) {
            if (connectivityFeedback != null) {
                connectivityFeedback.recordError();
            }
            sentry.addBreadcrumb("sync.tickets", "API Error: " + e.getMessage());
            throw new SyncException(e.getMessage());
        }
        sentry.addBreadcrumb("sync.queue", "Check-in upload complete");
    }

    public class SyncResult {
        private boolean dataUploaded;
        private boolean dataDownloaded;
        private Throwable exception;

        public SyncResult(boolean dataUploaded, boolean dataDownloaded, Throwable exception) {
            this.dataUploaded = dataUploaded;
            this.dataDownloaded = dataDownloaded;
            this.exception = exception;
        }

        public boolean isDataUploaded() {
            return dataUploaded;
        }

        public boolean isDataDownloaded() {
            return dataDownloaded;
        }

        public Throwable getException() {
            return exception;
        }
    }
}
