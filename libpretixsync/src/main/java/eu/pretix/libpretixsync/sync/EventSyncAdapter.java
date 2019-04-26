package eu.pretix.libpretixsync.sync;

import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import eu.pretix.libpretixsync.api.PretixApi;
import eu.pretix.libpretixsync.db.Event;
import io.requery.BlockingEntityStore;
import io.requery.Persistable;

public class EventSyncAdapter extends BaseSingleObjectSyncAdapter<Event> {
    public EventSyncAdapter(BlockingEntityStore<Persistable> store, String eventSlug, String key, PretixApi api, SyncManager.ProgressFeedback feedback) {
        super(store, eventSlug, key, api, feedback);
    }

    @Override
    public void updateObject(Event obj, JSONObject jsonobj) throws JSONException {
        obj.setSlug(jsonobj.getString("slug"));
        obj.setCurrency(jsonobj.getString("currency"));
        obj.setDate_from(ISODateTimeFormat.dateTimeParser().parseDateTime(jsonobj.getString("date_from")).toDate());
        if (!jsonobj.isNull("date_to")) {
            obj.setDate_to(ISODateTimeFormat.dateTimeParser().parseDateTime(jsonobj.getString("date_to")).toDate());
        }
        obj.setLive(jsonobj.getBoolean("live"));
        obj.setHas_subevents(jsonobj.getBoolean("has_subevents"));
        obj.setJson_data(jsonobj.toString());
    }

    Event getKnownObject() {
        List<Event> is = store.select(Event.class)
                .where(Event.SLUG.eq(key))
                .get().toList();
        if (is.size() == 0) {
            return null;
        } else if (is.size() == 1) {
            return is.get(0);
        } else {
            // What's going on here? Let's delete and re-fetch
            store.delete(is);
            return null;
        }
    }

    @Override
    protected String getUrl() {
        return api.organizerResourceUrl("events/" + key);
    }

    @Override
    String getResourceName() {
        return "events";
    }

    @Override
    Event newEmptyObject() {
        return new Event();
    }
}
