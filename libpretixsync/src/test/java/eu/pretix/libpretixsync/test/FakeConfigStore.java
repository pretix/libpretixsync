package eu.pretix.libpretixsync.test;

import eu.pretix.libpretixsync.api.PretixApi;
import eu.pretix.libpretixsync.config.ConfigStore;

public class FakeConfigStore implements ConfigStore {
    private long last_download;
    private long last_sync;
    private long last_failed_sync;
    private String last_failed_sync_msg;
    private String last_status_data;
    private boolean allow_search;

    public void setAllow_search(boolean allow_search) {
        this.allow_search = allow_search;
    }

    @Override
    public boolean isDebug() {
        return false;
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public int getApiVersion() {
        return PretixApi.SUPPORTED_API_VERSION;
    }

    @Override
    public String getApiUrl() {
        return "http://example.org";
    }

    @Override
    public String getApiKey() {
        return "12345";
    }

    @Override
    public String getOrganizerSlug() {
        return "demo";
    }

    @Override
    public String getEventSlug() {
        return "demo";
    }

    @Override
    public Long getSubEventId() {
        return null;
    }

    @Override
    public boolean getShowInfo() {
        return true;
    }

    @Override
    public boolean getAllowSearch() {
        return allow_search;
    }

    @Override
    public String getLastStatusData() {
        return last_status_data;
    }

    @Override
    public long getLastDownload() {
        return last_download;
    }

    @Override
    public void setLastDownload(long val) {
        last_download = val;
    }

    @Override
    public long getLastSync() {
        return last_sync;
    }

    @Override
    public void setLastSync(long val) {
        last_sync = val;
    }

    @Override
    public long getLastFailedSync() {
        return last_failed_sync;
    }

    @Override
    public void setLastFailedSync(long val) {
        last_failed_sync = val;
    }

    @Override
    public String getLastFailedSyncMsg() {
        return last_failed_sync_msg;
    }

    @Override
    public void setLastFailedSyncMsg(String val) {
        last_failed_sync_msg = val;
    }

    @Override
    public void setLastStatusData(String val) {
        last_status_data = val;
    }

    @Override
    public Long getPosId() {
        return null;
    }
}
