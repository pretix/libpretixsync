package eu.pretix.libpretixsync.sync;

import eu.pretix.libpretixsync.api.PretixApi;
import eu.pretix.libpretixsync.db.Event;
import io.requery.BlockingEntityStore;
import io.requery.Persistable;
import io.requery.query.Tuple;
import io.requery.util.CloseableIterator;

import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.List;

public class AllEventsSyncAdapter extends BaseDownloadSyncAdapter<Event, String> {
    public AllEventsSyncAdapter(BlockingEntityStore<Persistable> store, FileStorage fileStorage, PretixApi api, String syncCycleId, SyncManager.ProgressFeedback feedback) {
        super(store, fileStorage, "__all__", api, syncCycleId, feedback);
    }

    @Override
    CloseableIterator<Tuple> getKnownIDsIterator() {
        return store.select(Event.SLUG)
                .get().iterator();
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

    @Override
    protected String getUrl() {
        return api.organizerResourceUrl(getResourceName());
    }

    @Override
    String getResourceName() {
        return "events";
    }

    @Override
    String getId(JSONObject obj) throws JSONException {
        return obj.getString("slug");
    }

    @Override
    String getId(Event obj) {
        return obj.getSlug();
    }

    @Override
    Event newEmptyObject() {
        return new Event();
    }

    @Override
    public CloseableIterator<Event> runBatch(List<String> parameterBatch) {
        return store.select(Event.class)
                .where(Event.SLUG.in(parameterBatch))
                .get().iterator();
    }
}
