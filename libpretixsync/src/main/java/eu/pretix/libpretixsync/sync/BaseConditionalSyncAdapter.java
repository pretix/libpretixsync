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

    @Override
    protected JSONObject downloadPage(String url, boolean isFirstPage) throws ApiException, ResourceNotModified {
        ResourceLastModified resourceLastModified = store.select(ResourceLastModified.class)
                .where(ResourceLastModified.RESOURCE.eq(getResourceName()))
                .limit(1)
                .get().firstOrNull();
        if (resourceLastModified == null) {
            resourceLastModified = new ResourceLastModified();
            resourceLastModified.setResource(getResourceName());
        }

        PretixApi.ApiResponse apiResponse = api.fetchResource(url, resourceLastModified.getLast_modified());

        if (isFirstPage && apiResponse.getResponse().header("Last-Modified") != null) {
            resourceLastModified.setLast_modified(apiResponse.getResponse().header("Last-Modified"));
            store.upsert(resourceLastModified);
        }
        return apiResponse.getData();
    }
}
