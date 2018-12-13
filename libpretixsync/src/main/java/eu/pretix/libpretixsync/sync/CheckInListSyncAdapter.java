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
import eu.pretix.libpretixsync.db.Quota;
import io.requery.BlockingEntityStore;
import io.requery.Persistable;

public class CheckInListSyncAdapter extends BaseConditionalSyncAdapter<CheckInList, Long> {
    public CheckInListSyncAdapter(BlockingEntityStore<Persistable> store, FileStorage fileStorage, String eventSlug, PretixApi api) {
        super(store, fileStorage, eventSlug, api);
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
    Iterator<CheckInList> getKnownObjectsIterator() {
        return store.select(CheckInList.class)
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
    Long getId(CheckInList obj) {
        return obj.getServer_id();
    }

    @Override
    CheckInList newEmptyObject() {
        return new CheckInList();
    }
}
