package eu.pretix.libpretixsync.db;

import io.requery.*;
import org.json.JSONException;
import org.json.JSONObject;

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

    @Column(definition = "TEXT")
    public String json_data;

    @Override
    public JSONObject getJSON() throws JSONException {
        return new JSONObject(json_data);
    }
}
