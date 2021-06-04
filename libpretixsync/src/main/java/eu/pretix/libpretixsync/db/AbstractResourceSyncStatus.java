package eu.pretix.libpretixsync.db;

import io.requery.Entity;
import io.requery.Generated;
import io.requery.Index;
import io.requery.Key;

@Entity(cacheable = false)
public class AbstractResourceSyncStatus {
    @Generated
    @Key
    public Long id;

    @Index
    public String resource;

    public String last_modified;

    @Index
    public String event_slug;

    public String status;

    public String meta;
}
