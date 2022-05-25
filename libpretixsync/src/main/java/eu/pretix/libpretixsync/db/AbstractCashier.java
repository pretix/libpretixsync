package eu.pretix.libpretixsync.db;

import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
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

    public String login_method;

    public String pin;

    public String otp_key;

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
        // LOGIN_AUTO = 'a'
        // LOGIN_PIN = 'p'
        // LOGIN_OTP = 'o'
        // LOGIN_RFID = 'r'
        if (!this.active) {
            return false;
        }

        if (
                (this.login_method.contains("p") || this.login_method.isEmpty()) && this.pin.equals(pin)
        ) {
            return true;
        }

        if (this.login_method.contains("a")) {
            return true;
        }

        // TODO: Read Length from settings (pretixpos_cashier_otp_length)
        if (this.login_method.contains("o") && pin.length() == 6) {
            CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6);
            TimeProvider timeProvider = new SystemTimeProvider();
            CodeVerifier verifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
            return verifier.isValidCode(this.otp_key, pin);
        }

        return false;
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
    public long getNumericId() {
        return this.server_id;
    }

    @Override
    public String getUserId() {
        return this.userid;
    }
}
