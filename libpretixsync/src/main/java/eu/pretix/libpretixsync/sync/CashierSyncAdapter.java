package eu.pretix.libpretixsync.sync;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import eu.pretix.libpretixsync.api.PretixApi;
import eu.pretix.libpretixsync.db.Cashier;
import eu.pretix.libpretixsync.db.ItemCategory;
import io.requery.BlockingEntityStore;
import io.requery.Persistable;
import io.requery.query.Tuple;
import io.requery.util.CloseableIterator;

public class CashierSyncAdapter extends BaseConditionalSyncAdapter<Cashier, Long> {

    public CashierSyncAdapter(BlockingEntityStore<Persistable> store, FileStorage fileStorage, PretixApi api, String syncCycleId, SyncManager.ProgressFeedback feedback) {
        super(store, fileStorage, "__all__", api, syncCycleId, feedback);
    }

    @Override
    public void updateObject(Cashier obj, JSONObject jsonobj) throws JSONException {
        obj.setServer_id(jsonobj.getLong("id"));
        obj.setName(jsonobj.getString("name"));
        obj.setUserid(jsonobj.getString("userid"));
        obj.setLogin_method(jsonobj.getString("login_method"));
        obj.setPin(jsonobj.isNull("pin") ? "" : jsonobj.getString("pin"));
        obj.setOtp_key(jsonobj.isNull("otp_key") ? "" : jsonobj.getString("otp_key"));
        obj.setJson_data(jsonobj.toString());
        obj.setActive(jsonobj.getBoolean("active"));
    }

    @Override
    public CloseableIterator<Cashier> runBatch(List<Long> ids) {
        return store.select(Cashier.class)
                .where(Cashier.SERVER_ID.in(ids))
                .get().iterator();
    }

    @Override
    CloseableIterator<Tuple> getKnownIDsIterator() {
        return store.select(Cashier.SERVER_ID)
                .get().iterator();
    }

    @Override
    String getResourceName() {
        return "cashiers";
    }

    protected String getUrl() {
        return api.organizerResourceUrl("pos/" + getResourceName());
    }

    @Override
    Long getId(JSONObject obj) throws JSONException {
        return obj.getLong("id");
    }

    @Override
    Long getId(Cashier obj) {
        return obj.getServer_id();
    }

    @Override
    Cashier newEmptyObject() {
        return new Cashier();
    }
}
