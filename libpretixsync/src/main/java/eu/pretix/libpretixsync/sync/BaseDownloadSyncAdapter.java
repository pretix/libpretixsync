package eu.pretix.libpretixsync.sync;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import eu.pretix.libpretixsync.api.ApiException;
import eu.pretix.libpretixsync.api.PretixApi;
import eu.pretix.libpretixsync.api.ResourceNotModified;
import eu.pretix.libpretixsync.db.RemoteObject;
import eu.pretix.libpretixsync.utils.JSONUtils;
import io.requery.BlockingEntityStore;
import io.requery.Persistable;

public abstract class BaseDownloadSyncAdapter<T extends RemoteObject & Persistable, K> implements DownloadSyncAdapter {

    protected BlockingEntityStore<Persistable> store;
    protected PretixApi api;
    protected String eventSlug;
    protected FileStorage fileStorage;

    public BaseDownloadSyncAdapter(BlockingEntityStore<Persistable> store, FileStorage fileStorage, String eventSlug, PretixApi api) {
        this.store = store;
        this.api = api;
        this.eventSlug = eventSlug;
        this.fileStorage = fileStorage;
    }

    @Override
    public void download() throws JSONException, ApiException {
        try {
            List<JSONObject> data = downloadRawData();
            processData(data);
        } catch (ResourceNotModified e) {
            // Do nothing
        }
    }

    abstract Iterator<T> getKnownObjectsIterator();

    protected Map<K, T> getKnownObjects() {
        Iterator<T> it = getKnownObjectsIterator();
        Map<K, T> known = new HashMap<>();
        while (it.hasNext()) {
            T obj = it.next();
            known.put(getId(obj), obj);
        }
        return known;
    }

    protected void processData(final List<JSONObject> data) {
        store.runInTransaction(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                Map<K, T> known = getKnownObjects();
                Set<K> seen = new HashSet<>();
                List<T> inserts = new ArrayList<>();

                for (JSONObject jsonobj : data) {
                    K jsonid = getId(jsonobj);
                    T obj;
                    JSONObject old = null;
                    if (seen.contains(jsonid)) {
                        continue;
                    } else if (known.containsKey(jsonid)) {
                        obj = known.get(jsonid);
                        old = obj.getJSON();
                    } else {
                        obj = newEmptyObject();
                    }
                    if (known.containsKey(jsonid)) {
                        known.remove(jsonid);
                        if (!JSONUtils.similar(jsonobj, old)) {
                            updateObject(obj, jsonobj);
                            store.update(obj);
                        }
                    } else {
                        updateObject(obj, jsonobj);
                        if (autoPersist()) {
                            inserts.add(obj);
                        }
                    }
                    seen.add(jsonid);
                }
                store.insert(inserts);
                if (deleteUnseen()) {
                    for (T obj : known.values()) {
                        prepareDelete(obj);
                        store.delete(obj);
                    }
                }
                return null;
            }
        });
    }

    protected void prepareDelete(T obj) {

    }

    protected boolean autoPersist() {
        return true;
    }

    protected boolean deleteUnseen() {
        return true;
    }

    public abstract void updateObject(T obj, JSONObject jsonobj) throws JSONException;

    protected List<JSONObject> downloadRawData() throws JSONException, ApiException, ResourceNotModified {
        List<JSONObject> result = new ArrayList<>();
        String url = api.eventResourceUrl(getResourceName());
        boolean isFirstPage = true;
        while (true) {
            JSONObject page = downloadPage(url, isFirstPage);
            for (int i = 0; i < page.getJSONArray("results").length(); i++) {
                result.add(page.getJSONArray("results").getJSONObject(i));
            }
            if (page.isNull("next")) {
                break;
            }
            url = page.getString("next");
            isFirstPage = false;
        }
        return result;
    }

    protected JSONObject downloadPage(String url, boolean isFirstPage) throws ApiException, ResourceNotModified {
        return api.fetchResource(url).getData();
    }

    abstract String getResourceName();

    abstract K getId(JSONObject obj) throws JSONException;

    abstract K getId(T obj);

    abstract T newEmptyObject();
}
