package eu.pretix.libpretixsync.sync;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ExecutionException;

import eu.pretix.libpretixsync.api.ApiException;
import eu.pretix.libpretixsync.api.PretixApi;
import eu.pretix.libpretixsync.api.ResourceNotModified;
import eu.pretix.libpretixsync.db.RemoteObject;
import eu.pretix.libpretixsync.db.ResourceLastModified;
import io.requery.BlockingEntityStore;
import io.requery.Persistable;

abstract public class BaseConditionalSyncAdapter<T extends RemoteObject & Persistable, K> extends BaseDownloadSyncAdapter<T, K> {
    public BaseConditionalSyncAdapter(BlockingEntityStore<Persistable> store, FileStorage fileStorage, String eventSlug, PretixApi api, SyncManager.ProgressFeedback feedback) {
        super(store, fileStorage, eventSlug, api, feedback);
    }

    private PretixApi.ApiResponse firstResponse;

    @Override
    protected JSONObject downloadPage(String url, boolean isFirstPage) throws ApiException, ResourceNotModified {
        ResourceLastModified resourceLastModified = store.select(ResourceLastModified.class)
                .where(ResourceLastModified.RESOURCE.eq(getResourceName()))
                .and(ResourceLastModified.EVENT_SLUG.eq(eventSlug))
                .limit(1)
                .get().firstOrNull();
        if (resourceLastModified == null) {
            resourceLastModified = new ResourceLastModified();
        } else {
            if (!getMeta().equals(resourceLastModified.getMeta()) && !(getMeta().equals("") && resourceLastModified.getMeta() == null)) {
                store.delete(resourceLastModified);
                resourceLastModified = new ResourceLastModified();
            }
        }
        PretixApi.ApiResponse apiResponse = api.fetchResource(url, resourceLastModified.getLast_modified());
        if (isFirstPage) {
            firstResponse = apiResponse;
        }
        return apiResponse.getData();
    }

    public String getMeta() {
        return "";
    }

    @Override
    public void download() throws JSONException, ApiException, ExecutionException, InterruptedException {
        firstResponse = null;
        super.download();
        if (firstResponse != null) {
            ResourceLastModified resourceLastModified = store.select(ResourceLastModified.class)
                    .where(ResourceLastModified.RESOURCE.eq(getResourceName()))
                    .and(ResourceLastModified.EVENT_SLUG.eq(eventSlug))
                    .limit(1)
                    .get().firstOrNull();
            if (resourceLastModified == null) {
                resourceLastModified = new ResourceLastModified();
                resourceLastModified.setResource(getResourceName());
                resourceLastModified.setEvent_slug(eventSlug);
                resourceLastModified.setMeta(getMeta());
            }
            if (firstResponse.getResponse().header("Last-Modified") != null) {
                resourceLastModified.setLast_modified(firstResponse.getResponse().header("Last-Modified"));
                resourceLastModified.setMeta(getMeta());
                store.upsert(resourceLastModified);
            }
            firstResponse = null;
        }
    }
}
