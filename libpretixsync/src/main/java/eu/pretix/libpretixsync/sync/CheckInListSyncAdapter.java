package eu.pretix.libpretixsync.sync;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import eu.pretix.libpretixsync.api.PretixApi;
import eu.pretix.libpretixsync.db.CheckInList;
import eu.pretix.libpretixsync.db.Item;
import eu.pretix.libpretixsync.db.Migrations;
import eu.pretix.libpretixsync.utils.JSONUtils;
import io.requery.BlockingEntityStore;
import io.requery.Persistable;
import io.requery.query.Tuple;
import io.requery.util.CloseableIterator;

public class CheckInListSyncAdapter extends BaseConditionalSyncAdapter<CheckInList, Long> {
    private Long subeventId;

    public CheckInListSyncAdapter(BlockingEntityStore<Persistable> store, FileStorage fileStorage, String eventSlug, PretixApi api, String syncCycleId, SyncManager.ProgressFeedback feedback, Long subeventId) {
        super(store, fileStorage, eventSlug, api, syncCycleId, feedback);
        this.subeventId = subeventId;
    }

    protected String getUrl() {
        String url = api.eventResourceUrl(getResourceName());
        url += "?exclude=checkin_count&exclude=position_count";
        if (this.subeventId != null && this.subeventId > 0L) {
            url += "&subevent_match=" + this.subeventId;
        }
        return url;
    }

    @Override
    public void updateObject(CheckInList obj, JSONObject jsonobj) throws JSONException {
        obj.setEvent_slug(eventSlug);
        obj.setServer_id(jsonobj.getLong("id"));
        obj.setSubevent_id(jsonobj.optLong("subevent"));
        obj.setName(jsonobj.optString("name", ""));
        obj.setInclude_pending(jsonobj.optBoolean("include_pending"));
        obj.setAll_items(jsonobj.optBoolean("all_products"));
        obj.setJson_data(jsonobj.toString());
        JSONArray itemsarr = jsonobj.getJSONArray("limit_products");
        List<Long> itemids = new ArrayList<>();
        for (int i = 0; i < itemsarr.length(); i++) {
            itemids.add(itemsarr.getLong(i));
        }
        if (!itemids.isEmpty()) {
            List<Item> items = store.select(Item.class).where(
                    Item.SERVER_ID.in(itemids)
            ).get().toList();
            for (Item item : items) {
                if (!obj.getItems().contains(item)) {
                    obj.getItems().add(item);
                }
            }
            obj.getItems().retainAll(items);
        } else {
            obj.getItems().clear();
        }
    }


    @Override
    public CloseableIterator<CheckInList> runBatch(List<Long> ids) {
        return store.select(CheckInList.class)
                .where(CheckInList.EVENT_SLUG.eq(eventSlug))
                .and(CheckInList.SERVER_ID.in(ids))
                .get().iterator();
    }

    @Override
    CloseableIterator<Tuple> getKnownIDsIterator() {
        return store.select(CheckInList.SERVER_ID)
                .where(CheckInList.EVENT_SLUG.eq(eventSlug))
                .get().iterator();
    }

    @Override
    String getResourceName() {
        return "checkinlists";
    }

    @Override
    Long getId(JSONObject obj) throws JSONException {
        return obj.getLong("id");
    }

    @Override
    public String getMeta() {
        if (this.subeventId != null && this.subeventId > 0L) {
            return "subevent=" + this.subeventId;
        } else {
            return super.getMeta();
        }
    }

    @Override
    Long getId(CheckInList obj) {
        return obj.getServer_id();
    }

    @Override
    CheckInList newEmptyObject() {
        return new CheckInList();
    }

    public void standaloneRefreshFromJSON(JSONObject data) throws JSONException {
        CheckInList obj = store.select(CheckInList.class)
                .where(CheckInList.SERVER_ID.eq(data.getLong("id")))
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
