package eu.pretix.libpretixsync.sync;

import org.json.JSONException;

import eu.pretix.libpretixsync.api.ApiException;

public interface DownloadSyncAdapter {
    public void download() throws JSONException, ApiException;
}
