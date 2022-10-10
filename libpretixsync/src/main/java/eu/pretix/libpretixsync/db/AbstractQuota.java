package eu.pretix.libpretixsync.db;

import io.requery.*;
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

@Entity(cacheable = false)
public class AbstractQuota implements RemoteObject {

    @Generated
    @Key
    public Long id;

    public Long server_id;

    public String event_slug;

    public Long subevent_id;

    @Column(definition = "TEXT")
    public String json_data;

    @ManyToMany(cascade = CascadeAction.NONE)
    @JunctionTable
    List<Item> items;

    @Column(nullable = true)
    public Long size;

    @Column(nullable = true)
    public Long available;

    @Column(nullable = true)
    public Long available_number;

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
            return appliesToVariation(getJSON(), var);
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public JSONObject getJSON() throws JSONException {
        return new JSONObject(json_data);
    }

    public static boolean appliesToVariation(JSONObject quotaJson, Long varId) {
        try {
            JSONArray ja = quotaJson.getJSONArray("variations");
            for (int i = 0; i < ja.length(); i++) {
                if (ja.getLong(i) == varId) {
                    return true;
                }
            }
            return false;
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean appliesToVariation(JSONObject quotaJson, ItemVariation var) {
        return appliesToVariation(quotaJson, var.getServer_id());
    }
}
