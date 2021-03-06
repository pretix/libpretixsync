package eu.pretix.libpretixsync.db;

import io.requery.*;
import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.Date;

@Entity(cacheable = false)
public class AbstractCheckIn implements RemoteObject {

    @Generated
    @Key
    public Long id;

    @Column
    @Index
    public Long listId;

    @Nullable
    @Index
    public Long server_id;

    public Date datetime;

    @Column
    @ForeignKey(update = ReferentialAction.CASCADE)
    @ManyToOne
    @Index
    public OrderPosition position;

    @Column(definition = "TEXT")
    public String json_data;

    @Column
    public String type;

    @Override
    public JSONObject getJSON() throws JSONException {
        return new JSONObject(json_data);
    }

    public String getType() {
        try {
            return getJSON().getString("type");
        } catch (JSONException e) {
            return "entry";
        }
    }

    public Date getFullDatetime() {
        try {
            return ISODateTimeFormat.dateTimeParser().parseDateTime(getJSON().getString("datetime")).toDate();
        } catch (JSONException e) {
            e.printStackTrace();
            return datetime;
        }
    }
}
