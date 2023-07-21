package eu.pretix.libpretixsync.db;

import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

import io.requery.Column;
import io.requery.Entity;
import io.requery.ForeignKey;
import io.requery.Generated;
import io.requery.Index;
import io.requery.Key;
import io.requery.ManyToOne;
import io.requery.Nullable;
import io.requery.ReferentialAction;

@Entity(cacheable = false)
public class AbstractReusableMedium implements RemoteObject {

    @Generated
    @Key
    public Long id;

    @Nullable
    @Index
    public Long server_id;

    public String type;

    @Index
    public String identifier;

    public boolean active;

    public String expires;

    @Nullable
    public Long customer_id;

    @Nullable
    public Long linked_orderposition_id;

    @Nullable
    public Long linked_giftcard_id;

    @Column(definition = "TEXT")
    public String json_data;

    @Override
    public JSONObject getJSON() throws JSONException {
        return new JSONObject(json_data);
    }

    public boolean isExpired() {
        try {
            String expires = getJSON().optString("expires");
            if (expires == null) {
                return false;
            }
            return ISODateTimeFormat.dateTimeParser().parseDateTime(getJSON().getString("expires")).isBeforeNow();
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }
}
