package eu.pretix.libpretixsync.sync;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import eu.pretix.libpretixsync.api.PretixApi;
import eu.pretix.libpretixsync.db.BadgeLayout;
import eu.pretix.libpretixsync.db.BadgeLayoutItem;
import eu.pretix.libpretixsync.db.Item;
import io.requery.BlockingEntityStore;
import io.requery.Persistable;
import io.requery.query.Tuple;
import io.requery.util.CloseableIterator;

public class BadgeLayoutItemSyncAdapter extends BaseDownloadSyncAdapter<BadgeLayoutItem, Long> {
    private Map<Long, Item> itemCache = new HashMap<>();
    private Map<Long, BadgeLayout> layoutCache = new HashMap<>();

    public BadgeLayoutItemSyncAdapter(BlockingEntityStore<Persistable> store, FileStorage fileStorage, String eventSlug, PretixApi api, SyncManager.ProgressFeedback feedback) {
        super(store, fileStorage, eventSlug, api, feedback);
    }

    private Item getItem(long id) {
        if (itemCache.size() == 0) {
            List<Item> items = store
                    .select(Item.class)
                    .where(Item.EVENT_SLUG.eq(eventSlug))
                    .get().toList();
            for (Item item : items) {
                itemCache.put(item.getServer_id(), item);
            }
        }
        return itemCache.get(id);
    }

    private BadgeLayout getLayout(long id) {
        if (layoutCache.size() == 0) {
            List<BadgeLayout> items = store
                    .select(BadgeLayout.class)
                    .where(BadgeLayout.EVENT_SLUG.eq(eventSlug))
                    .get().toList();
            for (BadgeLayout item : items) {
                layoutCache.put(item.getServer_id(), item);
            }
        }
        return layoutCache.get(id);
    }

    @Override
    public void updateObject(BadgeLayoutItem obj, JSONObject jsonobj) throws JSONException {
        obj.setItem(getItem(jsonobj.getLong("item")));
        if (!jsonobj.isNull("layout")) {
            obj.setLayout(getLayout(jsonobj.getLong("layout")));
        } else {
            obj.setLayout(null);
        }
        obj.setServer_id(jsonobj.getLong("id"));
        obj.setJson_data(jsonobj.toString());
    }

    @Override
    public CloseableIterator<BadgeLayoutItem> runBatch(List<Long> ids) {
        return store.select(BadgeLayoutItem.class)
                .leftJoin(Item.class).on(Item.ID.eq(BadgeLayoutItem.ITEM_ID))
                .where(Item.EVENT_SLUG.eq(eventSlug))
                .and(BadgeLayoutItem.SERVER_ID.in(ids))
                .get().iterator();
    }

    @Override
    CloseableIterator<Tuple> getKnownIDsIterator() {
        return store.select(BadgeLayoutItem.SERVER_ID)
                .leftJoin(Item.class).on(Item.ID.eq(BadgeLayoutItem.ITEM_ID))
                .where(Item.EVENT_SLUG.eq(eventSlug))
                .get().iterator();
    }

    @Override
    String getResourceName() {
        return "badgeitems";
    }

    @Override
    Long getId(JSONObject obj) throws JSONException {
        return obj.getLong("id");
    }

    @Override
    Long getId(BadgeLayoutItem obj) {
        return obj.getServer_id();
    }

    @Override
    BadgeLayoutItem newEmptyObject() {
        return new BadgeLayoutItem();
    }
}
