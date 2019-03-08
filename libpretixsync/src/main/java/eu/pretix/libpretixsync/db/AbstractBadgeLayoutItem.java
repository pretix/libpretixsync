package eu.pretix.libpretixsync.db;

import org.json.JSONException;
import org.json.JSONObject;

import io.requery.Entity;
import io.requery.ForeignKey;
import io.requery.Generated;
import io.requery.Key;
import io.requery.ManyToOne;
import io.requery.Nullable;
import io.requery.ReferentialAction;

@Entity(cacheable = false)
public class AbstractBadgeLayoutItem implements RemoteObject {
    @Generated
    @Key
    public Long id;

    public Long server_id;

    @ForeignKey(update = ReferentialAction.CASCADE)
    @ManyToOne
    @Nullable
    public BadgeLayout layout;

    @ForeignKey(update = ReferentialAction.CASCADE)
    @ManyToOne
    public Item item;

    public String json_data;

    @Override
    public JSONObject getJSON() throws JSONException {
        return new JSONObject(json_data);
    }
}
