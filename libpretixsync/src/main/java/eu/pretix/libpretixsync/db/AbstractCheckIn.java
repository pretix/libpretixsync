package eu.pretix.libpretixsync.db;

import org.json.JSONException;
import org.json.JSONObject;

import java.sql.Timestamp;

import io.requery.Column;
import io.requery.Entity;
import io.requery.ForeignKey;
import io.requery.Generated;
import io.requery.Key;
import io.requery.ManyToOne;
import io.requery.ReferentialAction;

@Entity(cacheable = false)
public class AbstractCheckIn implements RemoteObject {

    @Generated
    @Key
    public Long id;

    @Column
    @ForeignKey(update = ReferentialAction.CASCADE)
    @ManyToOne
    public CheckInList list;

    public Timestamp datetime;

    @Column
    @ForeignKey(update = ReferentialAction.CASCADE)
    @ManyToOne
    public OrderPosition position;

    @Column(definition = "TEXT")
    public String json_data;

    @Override
    public JSONObject getJSON() throws JSONException {
        return new JSONObject(json_data);
    }

}
