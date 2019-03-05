package eu.pretix.libpretixsync.sync;

import org.json.JSONException;

import java.util.concurrent.ExecutionException;

import eu.pretix.libpretixsync.api.ApiException;

public interface DownloadSyncAdapter {
    public void download() throws JSONException, ApiException, ExecutionException, InterruptedException;
}
