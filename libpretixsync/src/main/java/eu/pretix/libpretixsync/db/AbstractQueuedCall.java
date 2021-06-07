package eu.pretix.libpretixsync.db;

import io.requery.Column;
import org.joda.time.format.ISODateTimeFormat;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import io.requery.Entity;
import io.requery.Generated;
import io.requery.Key;

@Entity(cacheable = false)
public abstract class AbstractQueuedCall {

    @Key
    @Generated
    public Long id;

    @Column(definition = "TEXT")
    public String url;

    @Column(definition = "TEXT")
    public String body;

    public String idempotency_key;
}
