package eu.pretix.libpretixsync.sync;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.ExecutionException;

import eu.pretix.libpretixsync.api.ApiException;
import eu.pretix.libpretixsync.api.PretixApi;
import eu.pretix.libpretixsync.api.ResourceNotModified;
import eu.pretix.libpretixsync.db.MediumKeySet;
import io.requery.BlockingEntityStore;
import io.requery.Persistable;
import io.requery.query.Tuple;
import io.requery.util.CloseableIterator;
import kotlin.NotImplementedError;

public class MediumKeySetSyncAdapter extends BaseDownloadSyncAdapter<MediumKeySet, Long> {
    JSONArray data;

    public MediumKeySetSyncAdapter(BlockingEntityStore<Persistable> store, FileStorage fileStorage, PretixApi api, String syncCycleId, SyncManager.ProgressFeedback feedback, JSONArray data) {
        super(store, fileStorage, "__all__", api, syncCycleId, feedback);
        this.data = data;
    }

    private String rlmName() {
        return "mediumkeyset";
    }

    @Override
    protected String getUrl() {
        throw new NotImplementedError();
    }

    @Override

    protected void downloadData() throws JSONException, ApiException, ResourceNotModified, ExecutionException, InterruptedException {
        asyncProcessPage(data).get();
    }

    @Override
    public void updateObject(MediumKeySet obj, JSONObject jsonobj) throws JSONException {
        obj.setPublic_id(jsonobj.getLong("public_id"));
        obj.setMedia_type(jsonobj.getString("media_type"));
        obj.setOrganizer(jsonobj.getString("organizer"));
        obj.setActive(jsonobj.getBoolean("active"));
        obj.setUid_key(jsonobj.getString("uid_key"));
        obj.setDiversification_key(jsonobj.getString("diversification_key"));
        obj.setJson_data(jsonobj.toString());
    }

    @Override
    protected boolean deleteUnseen() {
        return true;
    }

    @Override
    public CloseableIterator<MediumKeySet> runBatch(List<Long> ids) {
        return store.select(MediumKeySet.class)
                .where(MediumKeySet.PUBLIC_ID.in(ids))
                .get().iterator();
    }

    @Override
    CloseableIterator<Tuple> getKnownIDsIterator() {
        return store.select(MediumKeySet.PUBLIC_ID)
                .get().iterator();
    }

    @Override
    String getResourceName() {
        return "mediumkeyset";
    }

    @Override
    Long getId(JSONObject obj) throws JSONException {
        return obj.getLong("public_id");
    }

    @Override
    Long getId(MediumKeySet obj) {
        return obj.getPublic_id();
    }

    @Override
    MediumKeySet newEmptyObject() {
        return new MediumKeySet();
    }
}
