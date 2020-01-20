package eu.pretix.libpretixsync.sync;

import eu.pretix.libpretixsync.api.PretixApi;
import eu.pretix.libpretixsync.db.Event;
import eu.pretix.libpretixsync.db.SubEvent;
import io.requery.BlockingEntityStore;
import io.requery.Persistable;
import io.requery.query.Tuple;
import io.requery.util.CloseableIterator;

import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.List;

public class AllSubEventsSyncAdapter extends BaseDownloadSyncAdapter<SubEvent, Long> {
    public AllSubEventsSyncAdapter(BlockingEntityStore<Persistable> store, FileStorage fileStorage, String eventSlug, PretixApi api, SyncManager.ProgressFeedback feedback) {
        super(store, fileStorage, eventSlug, api, feedback);
    }

    @Override
    CloseableIterator<Tuple> getKnownIDsIterator() {
        return store.select(SubEvent.SERVER_ID)
                .get().iterator();
    }

    @Override
    public void updateObject(SubEvent obj, JSONObject jsonobj) throws JSONException {
        obj.setServer_id(jsonobj.getLong("id"));
        obj.setEvent_slug(jsonobj.getString("event"));
        obj.setDate_from(ISODateTimeFormat.dateTimeParser().parseDateTime(jsonobj.getString("date_from")).toDate());
        if (!jsonobj.isNull("date_to")) {
            obj.setDate_to(ISODateTimeFormat.dateTimeParser().parseDateTime(jsonobj.getString("date_to")).toDate());
        }
        obj.setActive(jsonobj.getBoolean("active"));
        obj.setJson_data(jsonobj.toString());
    }

    @Override
    protected String getUrl() {
        return api.organizerResourceUrl(getResourceName());
    }

    @Override
    String getResourceName() {
        return "subevents";
    }

    @Override
    Long getId(JSONObject obj) throws JSONException {
        return obj.getLong("id");
    }

    @Override
    Long getId(SubEvent obj) {
        return obj.getId();
    }

    @Override
    SubEvent newEmptyObject() {
        return new SubEvent();
    }

    @Override
    public CloseableIterator<SubEvent> runBatch(List<Long> parameterBatch) {
        return store.select(SubEvent.class)
                .where(SubEvent.SERVER_ID.in(parameterBatch))
                .get().iterator();
    }
}
