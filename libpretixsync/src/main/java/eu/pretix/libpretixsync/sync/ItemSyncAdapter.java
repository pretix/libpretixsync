package eu.pretix.libpretixsync.sync;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;

import eu.pretix.libpretixsync.api.ApiException;
import eu.pretix.libpretixsync.api.PretixApi;
import eu.pretix.libpretixsync.db.Item;
import eu.pretix.libpretixsync.db.Migrations;
import eu.pretix.libpretixsync.utils.HashUtils;
import eu.pretix.libpretixsync.utils.JSONUtils;
import io.requery.BlockingEntityStore;
import io.requery.Persistable;
import io.requery.query.Tuple;
import io.requery.util.CloseableIterator;

public class ItemSyncAdapter extends BaseConditionalSyncAdapter<Item, Long> {

    public ItemSyncAdapter(BlockingEntityStore<Persistable> store, FileStorage fileStorage, String eventSlug, PretixApi api, String syncCycleId, SyncManager.ProgressFeedback feedback) {
        super(store, fileStorage, eventSlug, api, syncCycleId, feedback);
    }

    @Override
    public void updateObject(Item obj, JSONObject jsonobj) throws JSONException {
        obj.setEvent_slug(eventSlug);
        obj.setServer_id(jsonobj.getLong("id"));
        obj.setPosition(jsonobj.getLong("position"));
        obj.setCategory_id(jsonobj.optLong("category"));
        obj.setAdmission(jsonobj.optBoolean("admission", false));
        obj.setActive(jsonobj.optBoolean("active", true));
        obj.setJson_data(jsonobj.toString());

        String remote_filename = jsonobj.optString("picture");
        if (remote_filename != null && remote_filename.startsWith("http")) {
            String hash = HashUtils.toSHA1(remote_filename.getBytes());
            String local_filename = "item_" + obj.getServer_id() + "_" + hash + remote_filename.substring(remote_filename.lastIndexOf("."));
            if (obj.getPicture_filename() != null && !obj.getPicture_filename().equals(local_filename)) {
                fileStorage.delete(obj.getPicture_filename());
                obj.setPicture_filename(null);
            }
            if (!fileStorage.contains(local_filename)) {
                try {
                    PretixApi.ApiResponse file = api.downloadFile(remote_filename);
                    OutputStream os = fileStorage.writeStream(local_filename);
                    InputStream is = file.getResponse().body().byteStream();
                    byte[] buffer = new byte[1444];
                    int byteread;
                    while ((byteread = is.read(buffer)) != -1) {
                        os.write(buffer, 0, byteread);
                    }
                    is.close();
                    os.close();
                    obj.setPicture_filename(local_filename);
                } catch (ApiException e) {
                    // TODO: What to do?
                    e.printStackTrace();
                } catch (IOException e) {
                    // TODO: What to do?
                    e.printStackTrace();
                    fileStorage.delete(local_filename);
                }
            } else {
                obj.setPicture_filename(local_filename);
            }
        } else {
            if (obj.getPicture_filename() != null) {
                fileStorage.delete(obj.getPicture_filename());
                obj.setPicture_filename(null);
            }
        }
    }

    @Override
    public CloseableIterator<Item> runBatch(List<Long> ids) {
        return store.select(Item.class)
                .where(Item.EVENT_SLUG.eq(eventSlug))
                .and(Item.SERVER_ID.in(ids))
                .get().iterator();
    }

    @Override
    CloseableIterator<Tuple> getKnownIDsIterator() {
        return store.select(Item.SERVER_ID)
                .where(Item.EVENT_SLUG.eq(eventSlug))
                .get().iterator();
    }

    @Override
    String getResourceName() {
        return "items";
    }

    @Override
    Long getId(JSONObject obj) throws JSONException {
        return obj.getLong("id");
    }

    @Override
    Long getId(Item obj) {
        return obj.getServer_id();
    }

    @Override
    Item newEmptyObject() {
        return new Item();
    }

    public void standaloneRefreshFromJSON(JSONObject data) throws JSONException {
        Item obj = store.select(Item.class)
                .where(Item.SERVER_ID.eq(data.getLong("id")))
                .get().firstOr(newEmptyObject());
        JSONObject old = null;
        if (obj.getId() != null) {
            old = obj.getJSON();
        }

        // Store object
        data.put("__libpretixsync_dbversion", Migrations.CURRENT_VERSION);
        data.put("__libpretixsync_syncCycleId", syncCycleId);
        if (old == null) {
            updateObject(obj, data);
            store.insert(obj);
        } else {
            if (!JSONUtils.similar(data, old)) {
                updateObject(obj, data);
                store.update(obj);
            }
        }
    }
}
