package eu.pretix.libpretixsync.sync;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import eu.pretix.libpretixsync.api.PretixApi;
import eu.pretix.libpretixsync.db.Item;
import eu.pretix.libpretixsync.db.Question;
import io.requery.BlockingEntityStore;
import io.requery.Persistable;

public class QuestionSyncAdapter extends BaseConditionalSyncAdapter<Question, Long> {
    public QuestionSyncAdapter(BlockingEntityStore<Persistable> store,FileStorage fileStorage, String eventSlug, PretixApi api) {
        super(store, fileStorage, eventSlug, api);
    }

    @Override
    public void updateObject(Question obj, JSONObject jsonobj) throws JSONException {
        obj.setEvent_slug(eventSlug);
        obj.setServer_id(jsonobj.getLong("id"));
        obj.setPosition(jsonobj.getLong("position"));
        obj.setRequired(jsonobj.optBoolean("required", false));
        obj.setJson_data(jsonobj.toString());
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
    Iterator<Question> getKnownObjectsIterator() {
        return store.select(Question.class)
                .where(Question.EVENT_SLUG.eq(eventSlug))
                .get().iterator();
    }

    @Override
    String getResourceName() {
        return "questions";
    }

    @Override
    Long getId(JSONObject obj) throws JSONException {
        return obj.getLong("id");
    }

    @Override
    Long getId(Question obj) {
        return obj.getServer_id();
    }

    @Override
    Question newEmptyObject() {
        return new Question();
    }
}
