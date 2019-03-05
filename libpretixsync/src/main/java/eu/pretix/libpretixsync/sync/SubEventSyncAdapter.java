package eu.pretix.libpretixsync.sync;

import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONException;
import org.json.JSONObject;

import eu.pretix.libpretixsync.api.PretixApi;
import eu.pretix.libpretixsync.db.SubEvent;
import io.requery.BlockingEntityStore;
import io.requery.Persistable;

public class SubEventSyncAdapter extends BaseSingleObjectSyncAdapter<SubEvent> {
    public SubEventSyncAdapter(BlockingEntityStore<Persistable> store, String eventSlug, String key, PretixApi api, SyncManager.ProgressFeedback feedback) {
        super(store, eventSlug, key, api, feedback);
    }

    @Override
    public void updateObject(SubEvent obj, JSONObject jsonobj) throws JSONException {
        obj.setServer_id(jsonobj.getLong("id"));
        obj.setEvent_slug(eventSlug);
        obj.setDate_from(ISODateTimeFormat.dateTimeParser().parseDateTime(jsonobj.getString("date_from")).toDate());
        if (!jsonobj.isNull("date_to")) {
            obj.setDate_to(ISODateTimeFormat.dateTimeParser().parseDateTime(jsonobj.getString("date_to")).toDate());
        }
        obj.setActive(jsonobj.getBoolean("active"));
        obj.setJson_data(jsonobj.toString());
    }

    SubEvent getKnownObject() {
        return store.select(SubEvent.class)
                .where(SubEvent.SERVER_ID.eq(Long.valueOf(key)))
                .get().firstOrNull();
    }

    @Override
    String getResourceName() {
        return "subevents";
    }

    @Override
    SubEvent newEmptyObject() {
        return new SubEvent();
    }
}
