package eu.pretix.libpretixsync.sync;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.concurrent.ExecutionException;

import eu.pretix.libpretixsync.api.ApiException;
import eu.pretix.libpretixsync.api.PretixApi;
import eu.pretix.libpretixsync.api.ResourceNotModified;
import eu.pretix.libpretixsync.db.BlockedTicketSecret;
import eu.pretix.libpretixsync.db.ResourceSyncStatus;
import io.requery.BlockingEntityStore;
import io.requery.Persistable;
import io.requery.query.Tuple;
import io.requery.util.CloseableIterator;

public class BlockedTicketSecretSyncAdapter extends BaseDownloadSyncAdapter<BlockedTicketSecret, Long> {
    private String firstResponseTimestamp;
    private ResourceSyncStatus rlm;

    public BlockedTicketSecretSyncAdapter(BlockingEntityStore<Persistable> store, FileStorage fileStorage, String eventSlug, PretixApi api, String syncCycleId, SyncManager.ProgressFeedback feedback) {
        super(store, fileStorage, eventSlug, api, syncCycleId, feedback);
    }

    @Override
    protected boolean autoPersist() {
        return false;
    }

    @Override
    public void download() throws JSONException, ApiException, ExecutionException, InterruptedException {
        boolean completed = false;
        try {
            super.download();
            completed = true;
        } finally {
            ResourceSyncStatus resourceSyncStatus = store.select(ResourceSyncStatus.class)
                    .where(ResourceSyncStatus.RESOURCE.eq(getResourceName()))
                    .and(ResourceSyncStatus.EVENT_SLUG.eq(eventSlug))
                    .limit(1)
                    .get().firstOrNull();

            // We need to cache the response timestamp of the *first* page in the result set to make
            // sure we don't miss anything between this and the next run.
            //
            // If the download failed, completed will be false. In case this was a full fetch
            // (i.e. no timestamp was stored beforehand) we will still store the timestamp to be
            // able to continue properly.
            if (firstResponseTimestamp != null) {
                if (resourceSyncStatus == null) {
                    resourceSyncStatus = new ResourceSyncStatus();
                    resourceSyncStatus.setResource(getResourceName());
                    resourceSyncStatus.setEvent_slug(eventSlug);
                    if (completed) {
                        resourceSyncStatus.setStatus("complete");
                        resourceSyncStatus.setLast_modified(firstResponseTimestamp);
                        store.upsert(resourceSyncStatus);
                    }
                } else {
                    if (completed) {
                        resourceSyncStatus.setLast_modified(firstResponseTimestamp);
                        store.upsert(resourceSyncStatus);
                    }
                }
            } else if (completed && resourceSyncStatus != null) {
                resourceSyncStatus.setStatus("complete");
                store.update(resourceSyncStatus);
            }
            firstResponseTimestamp = null;
        }

        // We clean up unblocked records after the sync
        store.delete(BlockedTicketSecret.class).where(BlockedTicketSecret.BLOCKED.eq(false));
    }

    protected boolean deleteUnseen() {
        return rlm == null;
    }

    @Override
    CloseableIterator<Tuple> getKnownIDsIterator() {
        return store.select(BlockedTicketSecret.SERVER_ID)
                .where(BlockedTicketSecret.EVENT_SLUG.eq(eventSlug))
                .get().iterator();
    }

    @Override
    public void updateObject(BlockedTicketSecret obj, JSONObject jsonobj) throws JSONException {
        obj.setEvent_slug(eventSlug);
        obj.setServer_id(jsonobj.getLong("id"));
        obj.setUpdated(jsonobj.getString("updated"));
        obj.setBlocked(jsonobj.getBoolean("blocked"));
        obj.setSecret(jsonobj.getString("secret"));
        obj.setJson_data(jsonobj.toString());

        if (obj.getId() == null && obj.isBlocked()) {
            // If not blocked and not yet in our database, we don't need to save it, as we only care
            // about blocked entries.
            store.insert(obj);
        } else {

        }
    }

    @Override
    protected String getUrl() {
        return api.eventResourceUrl(eventSlug, getResourceName());
    }

    @Override
    String getResourceName() {
        return "blockedsecrets";
    }

    @Override
    Long getId(JSONObject obj) throws JSONException {
        return obj.getLong("id");
    }

    @Override
    Long getId(BlockedTicketSecret obj) {
        return obj.getServer_id();
    }

    @Override
    BlockedTicketSecret newEmptyObject() {
        return new BlockedTicketSecret();
    }

    @Override
    public CloseableIterator<BlockedTicketSecret> runBatch(List<Long> parameterBatch) {
        return store.select(BlockedTicketSecret.class)
                .where(BlockedTicketSecret.SERVER_ID.in(parameterBatch))
                .and(BlockedTicketSecret.EVENT_SLUG.eq(eventSlug))
                .get().iterator();
    }

    @Override
    protected JSONObject downloadPage(String url, boolean isFirstPage) throws ApiException, ResourceNotModified {
        if (isFirstPage) {
            rlm = store.select(ResourceSyncStatus.class)
                    .where(ResourceSyncStatus.RESOURCE.eq(getResourceName()))
                    .and(ResourceSyncStatus.EVENT_SLUG.eq(eventSlug))
                    .limit(1)
                    .get().firstOrNull();
        }

        if (rlm != null) {
            // This resource has been fetched before.
            // Diff to last time

            // Ordering is crucial here: Only because the server returns the objects in the
            // order of modification we can be sure that we don't miss orders created in between our
            // paginated requests. If an object were to be modified between our fetch of page 1
            // and 2 that originally wasn't part of the result set, we won't see it (as it will
            // be inserted on page 1), but we'll see it the next time, and we will see some
            // duplicates on page 2, but we don't care. The important part is that nothing gets
            // lost "between the pages". If an order of page 2 gets modified and moves to page
            // one while we fetch page 2, again, we won't see it and we'll see some duplicates,
            // but the next sync will fix it since we always fetch our diff compared to the time
            // of the first page.
            try {
                if (!url.contains("updated_since")) {
                    if (url.contains("?")) {
                        url += "&";
                    } else {
                        url += "?";
                    }
                    url += "ordering=-updated&updated_since=" + URLEncoder.encode(rlm.getLast_modified(), "UTF-8");
                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }

        PretixApi.ApiResponse apiResponse = api.fetchResource(url);
        if (isFirstPage) {
            try {
                JSONArray results = apiResponse.getData().getJSONArray("results");
                if (results.length() > 0) {
                    firstResponseTimestamp = results.getJSONObject(0).getString("updated");
                }
            } catch (JSONException | NullPointerException e) {
                e.printStackTrace();
            }
        }
        return apiResponse.getData();
    }
}
