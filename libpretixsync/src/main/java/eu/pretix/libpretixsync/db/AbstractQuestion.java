package eu.pretix.libpretixsync.db;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import eu.pretix.libpretixsync.check.QuestionType;
import eu.pretix.libpretixsync.utils.I18nString;
import io.requery.*;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity(cacheable = false)
public class AbstractQuestion implements RemoteObject {

    public class ValidationException extends Exception {
        public ValidationException(String msg) {
            super(msg);
        }
    }

    @Generated
    @Key
    public Long id;

    public String event_slug;

    public Long server_id;

    public boolean required;

    public Long position;

    public String json_data;

    @ManyToMany(cascade = CascadeAction.NONE)
    @JunctionTable
    List<Item> items;

    @Override
    public JSONObject getJSON() throws JSONException {
        return new JSONObject(json_data);
    }

    public String getQuestion() {
        try {
            return I18nString.toString(getJSON().getJSONObject("question"));
        } catch (JSONException e) {
            e.printStackTrace();
            return "<invalid>";
        }
    }

    public boolean isAskDuringCheckin() {
        try {
            return getJSON().getBoolean("ask_during_checkin");
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }

    public QuestionType getType() {
        try {
            return QuestionType.valueOf(getJSON().getString("type"));
        } catch (JSONException e) {
            return QuestionType.T;
        }
    }

    public List<QuestionOption> getOptions() {
        List<QuestionOption> opts = new ArrayList<>();
        try {
            JSONArray arr = getJSON().getJSONArray("options");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject oobj = arr.getJSONObject(i);
                String answ;
                try {
                    answ = I18nString.toString(oobj.getJSONObject("answer"));
                } catch(JSONException e) {
                    answ = oobj.getString("answer");
                }
                opts.add(new QuestionOption(
                        oobj.getLong("id"),
                        oobj.getLong("position"),
                        oobj.getString("identifier"),
                        answ
                ));
            }
            return opts;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String clean_answer(String answer, List<QuestionOption> opts) throws ValidationException {
        QuestionType type = getType();
        if (required) {
            if (type == QuestionType.B) {
                if (!answer.equals("True") && !answer.equals("true")) {
                    throw new ValidationException("Question is required");
                }
            } else if (answer == null || answer.trim().equals("")) {
                throw new ValidationException("Question is required");
            }
        } else if ((answer == null || answer.trim().equals("")) && type != QuestionType.B) {
            return "";
        }
        if (type == QuestionType.N) {
            try {
                return new BigDecimal(answer.toString()).toPlainString();
            } catch (NumberFormatException e) {
                throw new ValidationException("Invalid number supplied");
            }
        } else if (type == QuestionType.B) {
            return (answer.equals("True") || answer.equals("true")) ? "True" : "False";
        } else if (type == QuestionType.C) {
            for (QuestionOption o : opts) {
                if (o.getServer_id().toString().equals(answer)) {
                    return answer;
                }
            }
            throw new ValidationException("Invalid choice supplied");
        } else if (type == QuestionType.M) {
            Set<String> validChoices = new HashSet<>();
            for (QuestionOption o : opts) {
                validChoices.add(o.getServer_id().toString());
            }
            for (String a : answer.split(",")) {
                if (!validChoices.contains(a)) {
                    throw new ValidationException("Invalid choice supplied");
                }
            }
        } else if (type == QuestionType.D) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            dateFormat.setLenient(false);
            try {
                dateFormat.parse(answer);
            } catch (ParseException e) {
                throw new ValidationException("Invalid date supplied");
            }
        } else if (type == QuestionType.H) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm");
            dateFormat.setLenient(false);
            try {
                dateFormat.parse(answer);
            } catch (ParseException e) {
                throw new ValidationException("Invalid time supplied");
            }
        } else if (type == QuestionType.W) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm");
            dateFormat.setLenient(false);
            try {
                dateFormat.parse(answer);
            } catch (ParseException e) {
                throw new ValidationException("Invalid datetime supplied");
            }
        }
        return answer;
    }
}
