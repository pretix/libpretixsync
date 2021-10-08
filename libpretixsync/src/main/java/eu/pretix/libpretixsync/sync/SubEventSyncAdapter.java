package eu.pretix.libpretixsync.sync;

import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import eu.pretix.libpretixsync.api.PretixApi;
import eu.pretix.libpretixsync.db.Migrations;
import eu.pretix.libpretixsync.db.SubEvent;
import eu.pretix.libpretixsync.utils.JSONUtils;
import io.requery.BlockingEntityStore;
import io.requery.Persistable;

public class SubEventSyncAdapter extends BaseSingleObjectSyncAdapter<SubEvent> {

    public SubEventSyncAdapter(BlockingEntityStore<Persistable> store, String eventSlug, String key, PretixApi api, String syncCycleId, SyncManager.ProgressFeedback feedback) {
        super(store, eventSlug, key, api, syncCycleId, feedback);
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
        List<SubEvent> is = store.select(SubEvent.class)
                .where(SubEvent.SERVER_ID.eq(Long.valueOf(key)))
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
    String getResourceName() {
        return "subevents";
    }

    @Override
    SubEvent newEmptyObject() {
        return new SubEvent();
    }


    public void standaloneRefreshFromJSON(JSONObject data) throws JSONException {
        SubEvent obj = store.select(SubEvent.class)
                .where(SubEvent.SERVER_ID.eq(data.getLong("id")))
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
