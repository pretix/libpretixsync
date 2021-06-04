package eu.pretix.libpretixsync.sync;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ExecutionException;

import eu.pretix.libpretixsync.api.ApiException;
import eu.pretix.libpretixsync.api.PretixApi;
import eu.pretix.libpretixsync.api.ResourceNotModified;
import eu.pretix.libpretixsync.db.RemoteObject;
import eu.pretix.libpretixsync.db.ResourceSyncStatus;
import io.requery.BlockingEntityStore;
import io.requery.Persistable;

abstract public class BaseConditionalSyncAdapter<T extends RemoteObject & Persistable, K> extends BaseDownloadSyncAdapter<T, K> {
    public BaseConditionalSyncAdapter(BlockingEntityStore<Persistable> store, FileStorage fileStorage, String eventSlug, PretixApi api, SyncManager.ProgressFeedback feedback) {
        super(store, fileStorage, eventSlug, api, feedback);
    }

    private PretixApi.ApiResponse firstResponse;

    @Override
    protected JSONObject downloadPage(String url, boolean isFirstPage) throws ApiException, ResourceNotModified {
        ResourceSyncStatus resourceSyncStatus = store.select(ResourceSyncStatus.class)
                .where(ResourceSyncStatus.RESOURCE.eq(getResourceName()))
                .and(ResourceSyncStatus.EVENT_SLUG.eq(eventSlug))
                .limit(1)
                .get().firstOrNull();
        if (resourceSyncStatus == null) {
            resourceSyncStatus = new ResourceSyncStatus();
        } else {
            if (!getMeta().equals(resourceSyncStatus.getMeta()) && !(getMeta().equals("") && resourceSyncStatus.getMeta() == null)) {
                store.delete(resourceSyncStatus);
                resourceSyncStatus = new ResourceSyncStatus();
            }
        }
        PretixApi.ApiResponse apiResponse = api.fetchResource(url, resourceSyncStatus.getLast_modified());
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
            ResourceSyncStatus resourceSyncStatus = store.select(ResourceSyncStatus.class)
                    .where(ResourceSyncStatus.RESOURCE.eq(getResourceName()))
                    .and(ResourceSyncStatus.EVENT_SLUG.eq(eventSlug))
                    .limit(1)
                    .get().firstOrNull();
            if (resourceSyncStatus == null) {
                resourceSyncStatus = new ResourceSyncStatus();
                resourceSyncStatus.setResource(getResourceName());
                resourceSyncStatus.setEvent_slug(eventSlug);
                resourceSyncStatus.setMeta(getMeta());
            }
            if (firstResponse.getResponse().header("Last-Modified") != null) {
                resourceSyncStatus.setLast_modified(firstResponse.getResponse().header("Last-Modified"));
                resourceSyncStatus.setMeta(getMeta());
                store.upsert(resourceSyncStatus);
            }
            firstResponse = null;
        }
    }
}
