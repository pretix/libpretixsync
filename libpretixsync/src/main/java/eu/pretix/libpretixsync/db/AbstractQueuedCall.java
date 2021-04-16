package eu.pretix.libpretixsync.db;

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

    public String url;

    public String body;

    public String idempotency_key;
}
