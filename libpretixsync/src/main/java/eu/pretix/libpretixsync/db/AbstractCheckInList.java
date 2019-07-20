package eu.pretix.libpretixsync.db;

import io.requery.*;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

@Entity(cacheable = false)
public class AbstractCheckInList implements RemoteObject {

    @Generated
    @Key
    public Long id;

    public Long server_id;

    public String event_slug;

    public String name;

    public Long subevent_id;

    public boolean include_pending;

    public boolean all_items;

    @Column(definition = "TEXT")
    public String json_data;

    @ManyToMany(cascade = CascadeAction.NONE)
    @JunctionTable
    List<Item> items;

    @Override
    public JSONObject getJSON() throws JSONException {
        return new JSONObject(json_data);
    }
}
