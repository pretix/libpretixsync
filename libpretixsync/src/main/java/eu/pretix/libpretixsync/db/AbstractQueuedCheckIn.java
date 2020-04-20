package eu.pretix.libpretixsync.db;

import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONException;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import io.requery.Entity;
import io.requery.Generated;
import io.requery.Key;

@Entity(cacheable = false)
public abstract class AbstractQueuedCheckIn {

    @Key
    @Generated
    public Long id;

    public String event_slug;

    public String secret;

    public String nonce;

    @Deprecated
    public Date datetime;  // For reasons outlied in https://github.com/pretix/pretixscan-proxy/issues/1 we store this as a string now
    // TODO: Remove old field in 2021 when no queued checkins from prior to this version should be in the field

    public String datetime_string;

    public String answers;

    public Long checkinListId;

    public void generateNonce() {
        this.nonce = NonceGenerator.nextNonce();
    }

    public static String formatDatetime(Date date) {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH); // Quoted "Z" to indicate UTC, no timezone offset
        df.setTimeZone(tz);
        return df.format(date);
    }

    public Date getFullDatetime() {
        if (datetime_string != null && !datetime_string.equals("")) {
            return ISODateTimeFormat.dateTimeParser().parseDateTime(datetime_string).toDate();
        } else {
            return datetime;
        }
    }
}
