package eu.pretix.libpretixsync.db;

import eu.pretix.libpretixsync.BuildConfig;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import io.requery.Column;
import io.requery.Entity;
import io.requery.Generated;
import io.requery.Key;

@Entity(cacheable = false)
public class AbstractCashier implements RemoteObject, CashierLike {
    @Generated
    @Key
    public Long id;

    public Long server_id;

    public String name;

    public String userid;

    public String pin;

    @Column(value = BuildConfig.BOOLEAN_FALSE, nullable = false)
    public boolean active;

    @Column(definition = "TEXT")
    public String json_data;

    @Override
    public JSONObject getJSON() throws JSONException {
        return new JSONObject(json_data);
    }

    @Override
    public boolean checkPIN(String pin) {
        if (!this.active) {
            return false;
        }
        return this.pin.equals(pin);
    }

    @Override
    public boolean validOnDevice(String device) {
        if (!this.active) {
            return false;
        }
        try {
            JSONObject team = getJSON().getJSONObject("team");
            if (team.optBoolean("all_devices", false)) {
                return true;
            }
            JSONArray devices = team.getJSONArray("devices");
            for (int i = 0; i < devices.length(); i++) {
                String d = devices.getString(i);
                if (d.equals(device)) {
                    return true;
                }
            }
            return false;
        } catch (JSONException e) {
            return false;
        }
    }

    @Override
    public boolean hasPermission(String permission) {
        Map<String, Boolean> defaults = new HashMap<>();
        defaults.put("can_open_drawer", true);
        defaults.put("can_top_up_gift_cards", true);
        defaults.put("can_check_in_tickets", true);
        if (!this.active) {
            return false;
        }
        try {
            JSONObject team = getJSON().getJSONObject("team");
            return team.optBoolean(permission, defaults.getOrDefault(permission, false));
        } catch (JSONException e) {
            return false;
        }
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Long getNumericId() {
        return this.server_id;
    }

    @Override
    public String getUserId() {
        return this.userid;
    }
}
