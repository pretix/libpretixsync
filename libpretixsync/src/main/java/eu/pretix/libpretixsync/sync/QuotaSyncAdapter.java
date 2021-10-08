package eu.pretix.libpretixsync.sync;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import eu.pretix.libpretixsync.api.PretixApi;
import eu.pretix.libpretixsync.db.Item;
import eu.pretix.libpretixsync.db.Quota;
import io.requery.BlockingEntityStore;
import io.requery.Persistable;
import io.requery.query.Tuple;
import io.requery.util.CloseableIterator;

public class QuotaSyncAdapter extends BaseDownloadSyncAdapter<Quota, Long> {
    private Long subeventId;

    public QuotaSyncAdapter(BlockingEntityStore<Persistable> store, FileStorage fileStorage, String eventSlug, PretixApi api, String syncCycleId, SyncManager.ProgressFeedback feedback, Long subeventId) {
        super(store, fileStorage, eventSlug, api, syncCycleId, feedback);
        this.subeventId = subeventId;
    }

    protected String getUrl() {
        String url = api.eventResourceUrl(getResourceName());
        url += "?with_availability=true";
        if (this.subeventId != null && this.subeventId > 0L) {
            url += "&subevent=" + this.subeventId;
        }
        return url;
    }

    @Override
    public void updateObject(Quota obj, JSONObject jsonobj) throws JSONException {
        obj.setEvent_slug(eventSlug);
        obj.setServer_id(jsonobj.getLong("id"));
        obj.setSubevent_id(jsonobj.optLong("subevent"));
        obj.setJson_data(jsonobj.toString());
        obj.setSize(jsonobj.isNull("size") ? null : jsonobj.getLong("size"));
        if (jsonobj.has("available")) {
            obj.setAvailable(jsonobj.getBoolean("available") ? 1L : 0L);
            obj.setAvailable_number(jsonobj.isNull("available_number") ? null : jsonobj.getLong("available_number"));
        } else {
            obj.setAvailable(null);
            obj.setAvailable_number(null);
        }
        JSONArray itemsarr = jsonobj.getJSONArray("items");
        List<Long> itemids = new ArrayList<>();
        for (int i = 0; i < itemsarr.length(); i++) {
            itemids.add(itemsarr.getLong(i));
        }
        List<Item> items = store.select(Item.class).where(
                Item.SERVER_ID.in(itemids)
        ).get().toList();
        for (Item item : items) {
            if (!obj.getItems().contains(item)) {
                obj.getItems().add(item);
            }
        }
        obj.getItems().retainAll(items);
    }

    @Override
    public CloseableIterator<Quota> runBatch(List<Long> ids) {
        return store.select(Quota.class)
                .where(Quota.EVENT_SLUG.eq(eventSlug))
                .and(Quota.SERVER_ID.in(ids))
                .get().iterator();
    }

    @Override
    CloseableIterator<Tuple> getKnownIDsIterator() {
        return store.select(Quota.SERVER_ID)
                .where(Quota.EVENT_SLUG.eq(eventSlug))
                .get().iterator();
    }

    @Override
    String getResourceName() {
        return "quotas";
    }

    @Override
    Long getId(JSONObject obj) throws JSONException {
        return obj.getLong("id");
    }

    @Override
    Long getId(Quota obj) {
        return obj.getServer_id();
    }

    @Override
    Quota newEmptyObject() {
        return new Quota();
    }
}
