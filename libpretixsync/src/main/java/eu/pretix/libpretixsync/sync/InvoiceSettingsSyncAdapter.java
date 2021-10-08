package eu.pretix.libpretixsync.sync;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import eu.pretix.libpretixsync.api.PretixApi;
import eu.pretix.libpretixsync.db.Settings;
import io.requery.BlockingEntityStore;
import io.requery.Persistable;

public class InvoiceSettingsSyncAdapter extends SettingsSyncAdapter {

    public InvoiceSettingsSyncAdapter(BlockingEntityStore<Persistable> store, String eventSlug, String key, PretixApi api, String syncCycleId, SyncManager.ProgressFeedback feedback) {
        super(store, eventSlug, key, api, syncCycleId, feedback);
    }

    @Override
    protected String getUrl() {
        return api.eventResourceUrl("invoicesettings");
    }
}
