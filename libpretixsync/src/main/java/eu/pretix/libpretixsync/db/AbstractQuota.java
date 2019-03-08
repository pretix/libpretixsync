package eu.pretix.libpretixsync.db;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import eu.pretix.libpretixsync.check.QuestionType;
import io.requery.CascadeAction;
import io.requery.Entity;
import io.requery.Generated;
import io.requery.JunctionTable;
import io.requery.Key;
import io.requery.ManyToMany;

@Entity(cacheable = false)
public class AbstractQuota implements RemoteObject {

    @Generated
    @Key
    public Long id;

    public Long server_id;

    public String event_slug;

    public Long subevent_id;

    public String json_data;

    @ManyToMany(cascade = CascadeAction.NONE)
    @JunctionTable
    List<Item> items;

    public boolean isUnlimited() {
        try {
            return getJSON().isNull("size");
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean appliesToVariation(ItemVariation var) {
        try {
            JSONArray ja = getJSON().getJSONArray("variations");
            for (int i = 0; i < ja.length(); i++) {
                if (ja.getLong(i) == var.getServer_id()) {
                    return true;
                }
            }
            return false;
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public JSONObject getJSON() throws JSONException {
        return new JSONObject(json_data);
    }
}
