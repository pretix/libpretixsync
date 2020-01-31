package eu.pretix.libpretixsync.config;

public interface ConfigStore {

    public boolean isDebug();

    public boolean isConfigured();

    public int getApiVersion();

    public String getApiUrl();

    public int getDeviceKnownVersion();

    public void setDeviceKnownVersion(int value);

    public String getApiKey();

    public String getOrganizerSlug();

    public String getEventSlug();

    public Long getSubEventId();

    public long getLastDownload();

    public void setLastDownload(long val);

    public long getLastSync();

    public void setLastSync(long val);

    public long getLastFailedSync();

    public void setLastFailedSync(long val);

    public String getLastFailedSyncMsg();

    public void setLastFailedSyncMsg(String val);

    public Long getPosId();

    public void setKnownPretixVersion(Long val);

    public Long getKnownPretixVersion();
}
