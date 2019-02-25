package eu.pretix.libpretixsync.sync;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import eu.pretix.libpretixsync.api.ApiException;
import eu.pretix.libpretixsync.api.PretixApi;
import eu.pretix.libpretixsync.api.ResourceNotModified;
import eu.pretix.libpretixsync.db.Order;
import eu.pretix.libpretixsync.db.OrderPosition;
import eu.pretix.libpretixsync.db.RemoteObject;
import eu.pretix.libpretixsync.db.ResourceLastModified;
import eu.pretix.libpretixsync.utils.JSONUtils;
import io.requery.BlockingEntityStore;
import io.requery.Persistable;

abstract public class BaseConditionalSyncAdapter<T extends RemoteObject & Persistable, K> extends BaseDownloadSyncAdapter<T, K> {
    public BaseConditionalSyncAdapter(BlockingEntityStore<Persistable> store, FileStorage fileStorage, String eventSlug, PretixApi api) {
        super(store, fileStorage, eventSlug, api);
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
        }
        PretixApi.ApiResponse apiResponse = api.fetchResource(url, resourceLastModified.getLast_modified());
        if (isFirstPage) {
            firstResponse = apiResponse;
        }
        return apiResponse.getData();
    }

    @Override
    public void download() throws JSONException, ApiException {
        super.download();
        if (firstResponse != null) {
            ResourceLastModified resourceLastModified = store.select(ResourceLastModified.class)
                    .where(ResourceLastModified.RESOURCE.eq("orders"))
                    .and(ResourceLastModified.EVENT_SLUG.eq(eventSlug))
                    .limit(1)
                    .get().firstOrNull();
            if (resourceLastModified == null) {
                resourceLastModified = new ResourceLastModified();
                resourceLastModified.setResource(getResourceName());
                resourceLastModified.setEvent_slug(eventSlug);
            }
            if (firstResponse.getResponse().header("Last-Modified") != null) {
                resourceLastModified.setLast_modified(firstResponse.getResponse().header("Last-Modified"));
                store.upsert(resourceLastModified);
            }
            firstResponse = null;
        }
    }
}
