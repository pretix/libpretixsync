package eu.pretix.libpretixsync.db;

import java.util.Date;

import io.requery.Column;
import io.requery.Entity;
import io.requery.ForeignKey;
import io.requery.Generated;
import io.requery.Key;
import io.requery.ManyToOne;
import io.requery.Nullable;

@Entity(cacheable = false)
public abstract class AbstractQueuedOrder {

    @Key
    @Generated
    public Long id;

    public String event_slug;

    public String payload;

    @Column(value = "false")
    public boolean locked;

    @ForeignKey
    @ManyToOne
    public Receipt receipt;

    @Nullable
    public String error;
}
