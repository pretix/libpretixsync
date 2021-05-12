package eu.pretix.libpretixsync.db;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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

    @Column(value = "false", nullable = false)
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
        if (!this.active) {
            return false;
        }
        try {
            JSONObject team = getJSON().getJSONObject("team");
            return team.optBoolean(permission, false);
        } catch (JSONException e) {
            return false;
        }
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public long getNumericId() {
        return this.server_id;
    }

    @Override
    public String getUserId() {
        return this.userid;
    }
}
