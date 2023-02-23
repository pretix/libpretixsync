package eu.pretix.libpretixsync.db;

import io.requery.*;

import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity(cacheable = false)
public class AbstractOrderPosition implements OrderPositionLike {

    @Generated
    @Key
    public Long id;

    public Long server_id;

    @Column(name = "order_ref")
    @ForeignKey(update = ReferentialAction.CASCADE)
    @Index
    @ManyToOne
    public Order order;

    public Long positionid;

    public Long subevent_id;

    public Long variation_id;

    @Nullable
    public String attendee_name;

    @Nullable
    public String attendee_email;

    @ForeignKey(update = ReferentialAction.SET_NULL)
    @ManyToOne
    public Item item;

    @Index
    public String secret;

    @Column(definition = "TEXT")
    public String json_data;

    @OneToMany
    public List<CheckIn> checkins;

    public boolean isBlocked() {
        try {
            JSONObject j = getJSON();
            if (!j.has("blocked") || j.isNull("blocked")) {
                return false;
            }
            return true;
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }

    public DateTime getValidFrom() {
        try {
            JSONObject j = getJSON();
            if (!j.has("valid_from") || j.isNull("valid_from")) {
                return null;
            }
            return ISODateTimeFormat.dateTimeParser().parseDateTime(j.getString("valid_from"));
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    public DateTime getValidUntil() {
        try {
            JSONObject j = getJSON();
            if (!j.has("valid_until") || j.isNull("valid_until")) {
                return null;
            }
            return ISODateTimeFormat.dateTimeParser().parseDateTime(j.getString("valid_until"));
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    public BigDecimal getPrice() {
        try {
            return new BigDecimal(getJSON().getString("price"));
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    public BigDecimal getTaxRate() {
        try {
            return new BigDecimal(getJSON().getString("tax_rate"));
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    public BigDecimal getTaxValue() {
        try {
            return new BigDecimal(getJSON().getString("tax_value"));
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Long getTaxRule() {
        try {
            long var = getJSON().optLong("tax_rule", 0L);
            if (var == 0) {
                return null;
            }
            return var;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Long getAddonToId() {
        try {
            long var = getJSON().optLong("addon_to", 0L);
            if (var == 0) {
                return null;
            }
            return var;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String getSeatName() {
        try {
            JSONObject seat = getJSON().optJSONObject("seat");
            if (seat != null) {
                return seat.getString("name");
            }
        } catch (JSONException e) {
        }
        return null;
    }

    public boolean hasAnyCheckins() {
        try {
            JSONArray checkins = getJSON().optJSONArray("checkins");
            return checkins.length() > 0;
        } catch (JSONException e) {
        }
        return false;
    }

    public Map<Long, String> getAnswers() {
        try {
            JSONArray arr = getJSON().getJSONArray("answers");
            Map<Long, String> res = new HashMap<>();
            for (int i = 0; i < arr.length(); i ++) {
                res.put(arr.getJSONObject(i).getLong("question"), arr.getJSONObject(i).getString("answer"));
            }
            return res;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Map<Long, String> getAnswersWithOptionIds() {
        try {
            JSONArray arr = getJSON().getJSONArray("answers");
            Map<Long, String> res = new HashMap<>();
            for (int i = 0; i < arr.length(); i ++) {
                JSONObject a = arr.getJSONObject(i);
                JSONArray opts = a.getJSONArray("options");
                if (opts.length() > 0) {
                    StringBuilder aw = new StringBuilder();
                    for (int j = 0; j < opts.length(); j ++) {
                        if (aw.length() > 0) {
                            aw.append(",");
                        }
                        aw.append(opts.getLong(j));
                    }
                    res.put(a.getLong("question"), aw.toString());
                } else {
                    res.put(a.getLong("question"), a.getString("answer"));
                }
            }
            return res;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Long getSubeventId() {
        try {
            long var = getJSON().optLong("subevent", 0L);
            if (var == 0) {
                return null;
            }
            return var;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Long getVariationId() {
        try {
            long var = getJSON().optLong("variation", 0L);
            if (var == 0) {
                return null;
            }
            return var;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public JSONObject getJSON() throws JSONException {
        return new JSONObject(json_data);
    }

    public void fromJSON(JSONObject data) throws JSONException {
        server_id = data.getLong("server_id");
        positionid = data.getLong("position");
        attendee_name = data.getString("attendee_name");
        attendee_email = data.getString("attendee_email");
        secret = data.getString("secret");
        json_data = data.toString();
    }

    @Override
    public String getAttendeeName() {
        return attendee_name;
    }

    public String getAttendeeEmail() {
        return attendee_email;
    }
}
