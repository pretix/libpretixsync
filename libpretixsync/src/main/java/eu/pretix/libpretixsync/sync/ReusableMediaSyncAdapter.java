package eu.pretix.libpretixsync.sync;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import eu.pretix.libpretixsync.api.ApiException;
import eu.pretix.libpretixsync.api.PretixApi;
import eu.pretix.libpretixsync.api.ResourceNotModified;
import eu.pretix.libpretixsync.db.CachedPdfImage;
import eu.pretix.libpretixsync.db.CheckIn;
import eu.pretix.libpretixsync.db.Item;
import eu.pretix.libpretixsync.db.Migrations;
import eu.pretix.libpretixsync.db.Order;
import eu.pretix.libpretixsync.db.OrderPosition;
import eu.pretix.libpretixsync.db.ResourceSyncStatus;
import eu.pretix.libpretixsync.db.ReusableMedium;
import eu.pretix.libpretixsync.utils.HashUtils;
import eu.pretix.libpretixsync.utils.JSONUtils;
import io.requery.BlockingEntityStore;
import io.requery.Persistable;
import io.requery.query.Tuple;
import io.requery.util.CloseableIterator;

public class ReusableMediaSyncAdapter extends BaseDownloadSyncAdapter<ReusableMedium, Long> {
    public ReusableMediaSyncAdapter(BlockingEntityStore<Persistable> store, FileStorage fileStorage, String eventSlug, PretixApi api, String syncCylceId, SyncManager.ProgressFeedback feedback) {
        super(store, fileStorage, "__all__", api, syncCylceId, feedback);
    }

    private String firstResponseTimestamp;
    private String lastMediumTimestamp;
    private ResourceSyncStatus rlm;

    private String rlmName() {
        return "reusablemedia";
    }

    @Override
    protected String getUrl() {
        return api.organizerResourceUrl(getResourceName());
    }

    @Override
    public void download() throws JSONException, ApiException, ExecutionException, InterruptedException {
        boolean completed = false;
        try {
            super.download();
            completed = true;
        } finally {
            ResourceSyncStatus resourceSyncStatus = store.select(ResourceSyncStatus.class)
                    .where(ResourceSyncStatus.RESOURCE.eq(rlmName()))
                    .and(ResourceSyncStatus.EVENT_SLUG.eq("__all__"))
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
                    resourceSyncStatus.setResource(rlmName());
                    resourceSyncStatus.setEvent_slug("__all__");
                    if (completed) {
                        resourceSyncStatus.setStatus("complete");
                    } else {
                        resourceSyncStatus.setStatus("incomplete:" + lastMediumTimestamp);
                    }
                    resourceSyncStatus.setLast_modified(firstResponseTimestamp);
                    store.upsert(resourceSyncStatus);
                } else {
                    if (completed) {
                        resourceSyncStatus.setLast_modified(firstResponseTimestamp);
                        store.upsert(resourceSyncStatus);
                    }
                }
            } else if (completed && resourceSyncStatus != null) {
                resourceSyncStatus.setStatus("complete");
                store.update(resourceSyncStatus);
            } else if (!completed && lastMediumTimestamp != null && resourceSyncStatus != null) {
                resourceSyncStatus.setStatus("incomplete:" + lastMediumTimestamp);
                store.update(resourceSyncStatus);
            }
            lastMediumTimestamp = null;
            firstResponseTimestamp = null;
        }
    }

    @Override
    public void updateObject(ReusableMedium obj, JSONObject jsonobj) throws JSONException {
        obj.setServer_id(jsonobj.getLong("id"));
        obj.setType(jsonobj.getString("type"));
        obj.setIdentifier(jsonobj.getString("identifier"));
        obj.setActive(jsonobj.getBoolean("active"));
        obj.setExpires(jsonobj.optString("expires"));
        obj.setCustomer_id(jsonobj.optLong("customer"));
        obj.setLinked_giftcard_id(jsonobj.optLong("linked_giftcard"));
        obj.setLinked_orderposition_id(jsonobj.optLong("linked_orderposition"));
        obj.setJson_data(jsonobj.toString());
    }

    @Override
    protected boolean deleteUnseen() {
        return false;
    }

    @Override
    protected JSONObject downloadPage(String url, boolean isFirstPage) throws ApiException, ResourceNotModified {
        if (isFirstPage) {
            rlm = store.select(ResourceSyncStatus.class)
                    .where(ResourceSyncStatus.RESOURCE.eq(rlmName()))
                    .and(ResourceSyncStatus.EVENT_SLUG.eq("__all__"))
                    .limit(1)
                    .get().firstOrNull();
        }
        boolean is_continued_fetch = false;

        if (rlm != null) {
            // This resource has been fetched before.
            if (rlm.getStatus() != null && rlm.getStatus().startsWith("incomplete:")) {
                // Continuing an interrupted fetch

                // Ordering is crucial here: Only because the server returns the orders in the
                // order of creation we can be sure that we don't miss orders created in between our
                // paginated requests.
                is_continued_fetch = true;
                try {
                    if (!url.contains("created_since")) {
                        url += "?ordering=datetime&created_since=" + URLEncoder.encode(rlm.getStatus().substring(11), "UTF-8");
                    }
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            } else {
                // Diff to last time

                // Ordering is crucial here: Only because the server returns the media in the
                // order of modification we can be sure that we don't miss media created in between our
                // paginated requests. If a medium were to be modified between our fetch of page 1
                // and 2 that originally wasn't part of the result set, we won't see it (as it will
                // be inserted on page 1), but we'll see it the next time, and we will se some
                // duplicates on page 2, but we don't care. The important part is that nothing gets
                // lost "between the pages". If a medium of page 2 gets modified and moves to page
                // one while we fetch page 2, again, we won't see it and we'll see some duplicates,
                // but the next sync will fix it since we always fetch our diff compared to the time
                // of the first page.
                try {
                    if (!url.contains("updated_since")) {
                        url += "?ordering=-updated&updated_since=" + URLEncoder.encode(rlm.getLast_modified(), "UTF-8");
                    }
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        }

        PretixApi.ApiResponse apiResponse = api.fetchResource(url);
        if (isFirstPage && !is_continued_fetch) {
            firstResponseTimestamp = apiResponse.getResponse().header("X-Page-Generated");
        }
        JSONObject d = apiResponse.getData();
        if (apiResponse.getResponse().code() == 200) {
            try {
                JSONArray res = d.getJSONArray("results");
                if (res.length() > 0) {
                    lastMediumTimestamp = res.getJSONObject(res.length() - 1).getString("created");
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return d;
    }

    @Override
    public CloseableIterator<ReusableMedium> runBatch(List<Long> ids) {
        return store.select(ReusableMedium.class)
                .where(ReusableMedium.SERVER_ID.in(ids))
                .get().iterator();
    }

    @Override
    CloseableIterator<Tuple> getKnownIDsIterator() {
        return store.select(ReusableMedium.SERVER_ID)
                .get().iterator();
    }

    @Override
    String getResourceName() {
        return "reusablemedia";
    }

    @Override
    Long getId(JSONObject obj) throws JSONException {
        return obj.getLong("id");
    }

    @Override
    Long getId(ReusableMedium obj) {
        return obj.getServer_id();
    }

    @Override
    ReusableMedium newEmptyObject() {
        return new ReusableMedium();
    }
}
