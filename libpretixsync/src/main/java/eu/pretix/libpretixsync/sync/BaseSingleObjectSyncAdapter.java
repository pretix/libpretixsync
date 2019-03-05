package eu.pretix.libpretixsync.sync;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.Callable;

import eu.pretix.libpretixsync.api.ApiException;
import eu.pretix.libpretixsync.api.PretixApi;
import eu.pretix.libpretixsync.api.ResourceNotModified;
import eu.pretix.libpretixsync.db.RemoteObject;
import eu.pretix.libpretixsync.utils.JSONUtils;
import io.requery.BlockingEntityStore;
import io.requery.Persistable;

public abstract class BaseSingleObjectSyncAdapter<T extends RemoteObject & Persistable> implements DownloadSyncAdapter {

    protected BlockingEntityStore<Persistable> store;
    protected PretixApi api;
    protected String eventSlug;
    protected String key;

    public BaseSingleObjectSyncAdapter(BlockingEntityStore<Persistable> store, String eventSlug, String key, PretixApi api) {
        this.store = store;
        this.api = api;
        this.eventSlug = eventSlug;
        this.key = key;
    }

    @Override
    public void download() throws JSONException, ApiException {
        try {
            JSONObject data = downloadRawData();
            processData(data);
        } catch (ResourceNotModified e) {
            // Do nothing
        }
    }

    abstract T getKnownObject();

    protected void processData(final JSONObject data) {
        store.runInTransaction(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                T known = getKnownObject();
                T obj;
                JSONObject old = null;
                if (known != null) {
                    obj = known;
                    old = obj.getJSON();
                    if (!JSONUtils.similar(data, old)) {
                        updateObject(obj, data);
                        store.update(obj);
                    }
                } else {
                    obj = newEmptyObject();
                    updateObject(obj, data);
                    store.insert(obj);
                }
                return null;
            }
        });
    }

    public abstract void updateObject(T obj, JSONObject jsonobj) throws JSONException;

    protected String getUrl() {
        return api.eventResourceUrl(getResourceName() + "/" + key);
    }

    protected JSONObject downloadRawData() throws ApiException, ResourceNotModified {
        return downloadPage(getUrl());
    }

    protected JSONObject downloadPage(String url) throws ApiException, ResourceNotModified {
        return api.fetchResource(url).getData();
    }

    abstract String getResourceName();

    abstract T newEmptyObject();
}
