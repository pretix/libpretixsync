package eu.pretix.libpretixsync.sync;

import eu.pretix.libpretixsync.api.ApiException;
import eu.pretix.libpretixsync.api.PretixApi;
import eu.pretix.libpretixsync.api.ResourceNotModified;
import eu.pretix.libpretixsync.db.Event;
import eu.pretix.libpretixsync.db.ResourceLastModified;
import eu.pretix.libpretixsync.db.SubEvent;
import io.requery.BlockingEntityStore;
import io.requery.Persistable;
import io.requery.query.Tuple;
import io.requery.util.CloseableIterator;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class AllSubEventsSyncAdapter extends BaseDownloadSyncAdapter<SubEvent, Long> {
    private String firstResponseTimestamp;
    private ResourceLastModified rlm;

    public AllSubEventsSyncAdapter(BlockingEntityStore<Persistable> store, FileStorage fileStorage, String eventSlug, PretixApi api, SyncManager.ProgressFeedback feedback) {
        super(store, fileStorage, eventSlug, api, feedback);
    }

    @Override
    public void download() throws JSONException, ApiException, ExecutionException, InterruptedException {
        boolean completed = false;
        try {
            super.download();
            completed = true;
        } finally {
            ResourceLastModified resourceLastModified = store.select(ResourceLastModified.class)
                    .where(ResourceLastModified.RESOURCE.eq("subevents"))
                    .and(ResourceLastModified.EVENT_SLUG.eq(eventSlug))
                    .limit(1)
                    .get().firstOrNull();

            // We need to cache the response timestamp of the *first* page in the result set to make
            // sure we don't miss anything between this and the next run.
            //
            // If the download failed, completed will be false. In case this was a full fetch
            // (i.e. no timestamp was stored beforehand) we will still store the timestamp to be
            // able to continue properly.
            if (firstResponseTimestamp != null) {
                if (resourceLastModified == null) {
                    resourceLastModified = new ResourceLastModified();
                    resourceLastModified.setResource("subevents");
                    resourceLastModified.setEvent_slug(eventSlug);
                    if (completed) {
                        resourceLastModified.setStatus("complete");
                        resourceLastModified.setLast_modified(firstResponseTimestamp);
                        store.upsert(resourceLastModified);
                    }
                } else {
                    if (completed) {
                        resourceLastModified.setLast_modified(firstResponseTimestamp);
                        store.upsert(resourceLastModified);
                    }
                }
            } else if (completed && resourceLastModified != null) {
                resourceLastModified.setStatus("complete");
                store.update(resourceLastModified);
            }
            firstResponseTimestamp = null;
        }
    }


    @Override
    CloseableIterator<Tuple> getKnownIDsIterator() {
        return store.select(SubEvent.SERVER_ID)
                .get().iterator();
    }

    @Override
    public void updateObject(SubEvent obj, JSONObject jsonobj) throws JSONException {
        obj.setServer_id(jsonobj.getLong("id"));
        obj.setEvent_slug(jsonobj.getString("event"));
        obj.setDate_from(ISODateTimeFormat.dateTimeParser().parseDateTime(jsonobj.getString("date_from")).toDate());
        if (!jsonobj.isNull("date_to")) {
            obj.setDate_to(ISODateTimeFormat.dateTimeParser().parseDateTime(jsonobj.getString("date_to")).toDate());
        }
        obj.setActive(jsonobj.getBoolean("active"));
        obj.setJson_data(jsonobj.toString());
    }

    @Override
    protected String getUrl() {
        return api.organizerResourceUrl(getResourceName());
    }

    @Override
    String getResourceName() {
        return "subevents";
    }

    @Override
    Long getId(JSONObject obj) throws JSONException {
        return obj.getLong("id");
    }

    @Override
    Long getId(SubEvent obj) {
        return obj.getId();
    }

    @Override
    SubEvent newEmptyObject() {
        return new SubEvent();
    }

    @Override
    public CloseableIterator<SubEvent> runBatch(List<Long> parameterBatch) {
        return store.select(SubEvent.class)
                .where(SubEvent.SERVER_ID.in(parameterBatch))
                .get().iterator();
    }

    @Override
    protected JSONObject downloadPage(String url, boolean isFirstPage) throws ApiException, ResourceNotModified {
        if (isFirstPage) {
            rlm = store.select(ResourceLastModified.class)
                    .where(ResourceLastModified.RESOURCE.eq("subevents"))
                    .and(ResourceLastModified.EVENT_SLUG.eq(eventSlug))
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
                if (!url.contains("modified_since")) {
                    if (url.contains("?")) {
                        url += "&";
                    } else {
                        url += "?";
                    }
                    url += "ordering=-last_modified&modified_since=" + URLEncoder.encode(rlm.getLast_modified(), "UTF-8");
                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }

        PretixApi.ApiResponse apiResponse = api.fetchResource(url);
        if (isFirstPage) {
            firstResponseTimestamp = apiResponse.getResponse().header("X-Page-Generated");
        }
        JSONObject d = apiResponse.getData();
        if (apiResponse.getResponse().code() == 200) {
            try {
                JSONArray res = d.getJSONArray("results");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return d;
    }
}
