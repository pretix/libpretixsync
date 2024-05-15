package eu.pretix.libpretixsync.sync;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import eu.pretix.libpretixsync.api.ApiException;
import eu.pretix.libpretixsync.api.PretixApi;
import eu.pretix.libpretixsync.db.BadgeLayout;
import eu.pretix.libpretixsync.db.Item;
import eu.pretix.libpretixsync.utils.HashUtils;
import io.requery.BlockingEntityStore;
import io.requery.Persistable;
import io.requery.query.Tuple;
import io.requery.util.CloseableIterator;

public class BadgeLayoutSyncAdapter extends BaseDownloadSyncAdapter<BadgeLayout, Long> {

    public BadgeLayoutSyncAdapter(BlockingEntityStore<Persistable> store, FileStorage fileStorage, String eventSlug, PretixApi api, String syncCycleId, SyncManager.ProgressFeedback feedback) {
        super(store, fileStorage, eventSlug, api, syncCycleId, feedback);
    }

    @Override
    public void updateObject(BadgeLayout obj, JSONObject jsonobj) throws JSONException {
        obj.setEvent_slug(eventSlug);
        obj.setIs_default(jsonobj.getBoolean("default"));
        obj.setServer_id(jsonobj.getLong("id"));
        obj.setJson_data(jsonobj.toString());

        String remote_filename = jsonobj.optString("background");
        if (remote_filename != null && remote_filename.startsWith("http")) {
            String hash = HashUtils.toSHA1(remote_filename.getBytes());
            String local_filename = "badgelayout_" + obj.getServer_id() + "_" + hash + ".pdf";
            if (obj.getBackground_filename() != null && !obj.getBackground_filename().equals(local_filename)) {
                fileStorage.delete(obj.getBackground_filename());
                obj.setBackground_filename(null);
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
                    obj.setBackground_filename(local_filename);
                } catch (ApiException e) {
                    // TODO: What to do?
                    e.printStackTrace();
                } catch (IOException e) {
                    // TODO: What to do?
                    e.printStackTrace();
                    fileStorage.delete(local_filename);
                }
            } else {
                obj.setBackground_filename(local_filename);
            }
        } else {
            if (obj.getBackground_filename() != null) {
                fileStorage.delete(obj.getBackground_filename());
                obj.setBackground_filename(null);
            }
        }
    }

    @Override
    protected void prepareDelete(BadgeLayout obj) {
        super.prepareDelete(obj);
        if (obj.getBackground_filename() != null) {
            fileStorage.delete(obj.getBackground_filename());
        }
    }

    @Override
    public CloseableIterator<BadgeLayout> runBatch(List<Long> ids) {
        return store.select(BadgeLayout.class)
                .where(BadgeLayout.EVENT_SLUG.eq(eventSlug))
                .and(BadgeLayout.SERVER_ID.in(ids))
                .get().iterator();
    }

    @Override
    CloseableIterator<Tuple> getKnownIDsIterator() {
        return store.select(BadgeLayout.SERVER_ID)
                .where(BadgeLayout.EVENT_SLUG.eq(eventSlug))
                .get().iterator();
    }

    @Override
    String getResourceName() {
        return "badgelayouts";
    }

    @Override
    Long getId(JSONObject obj) throws JSONException {
        return obj.getLong("id");
    }

    @Override
    Long getId(BadgeLayout obj) {
        return obj.getServer_id();
    }

    @Override
    BadgeLayout newEmptyObject() {
        return new BadgeLayout();
    }
}
