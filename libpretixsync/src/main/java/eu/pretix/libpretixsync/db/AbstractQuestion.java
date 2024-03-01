package eu.pretix.libpretixsync.db;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.joda.time.format.ISODateTimeFormat;
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
import java.util.Objects;
import java.util.Set;

@Entity(cacheable = false)
public class AbstractQuestion extends QuestionLike implements RemoteObject {

    @Generated
    @Key
    public Long id;

    public String event_slug;

    public Long server_id;

    public boolean required;

    public Long position;

    @Column(definition = "TEXT")
    public String json_data;

    @ManyToMany(cascade = CascadeAction.NONE)
    @JunctionTable
    @JsonIgnore
    List<Item> items;

    @Transient
    QuestionLike _resolvedDependency;

    @Transient
    boolean _resolveDependencyCalled;

    @Override
    public JSONObject getJSON() throws JSONException {
        return new JSONObject(json_data);
    }

    @Override
    public boolean requiresAnswer() {
        return required;
    }

    @Override
    public String getIdentifier() {
        try {
            return getJSON().getString("identifier");
        } catch (JSONException e) {
            e.printStackTrace();
            return "<invalid>";
        }
    }

    @Override
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

    public boolean isShowDuringCheckin() {
        try {
            return getJSON().getBoolean("show_during_checkin");
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean isHidden() {
        try {
            return getJSON().getBoolean("hidden");
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean isDependentOnOtherQuestion() {
        try {
            return getJSON().has("dependency_question") && !getJSON().isNull("dependency_question");
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }

    public Long getDependencyQuestionId() {
        try {
            return getJSON().optLong("dependency_question");
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void resolveDependency(List<AbstractQuestion> all) {
        Long id = getDependencyQuestionId();
        _resolveDependencyCalled = true;
        if (id == null) {
            _resolvedDependency = null;
            return;
        }
        for (AbstractQuestion q : all) {
            if (Objects.equals(q.server_id, id)) {
                _resolvedDependency = q;
                break;
            }
        }
    }

    @Override
    public QuestionLike getDependency() {
        if (!_resolveDependencyCalled) {
            throw new IllegalStateException("Question dependencies not resolved");
        }
        return _resolvedDependency;
    }

    public List<String> getDependencyValues() {
        try {
            List<String> l = new ArrayList<>();
            JSONArray a = getJSON().optJSONArray("dependency_values");
            if (a != null) {
                for (int i = 0; i < a.length(); i++) {
                    l.add(a.getString(i));
                }
            }
            return l;
        } catch (JSONException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public Long getValid_date_min() {
        try {
            if (!getJSON().isNull("valid_date_min")) {
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                dateFormat.setLenient(false);
                return dateFormat.parse(getJSON().getString("valid_date_min")).getTime();
            }
        } catch (JSONException | ParseException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Long getValid_date_max() {
        try {
            if (!getJSON().isNull("valid_date_max")) {
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                dateFormat.setLenient(false);
                return dateFormat.parse(getJSON().getString("valid_date_max")).getTime();
            }
        } catch (JSONException | ParseException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Long getValid_datetime_min() {
        try {
            if (!getJSON().isNull("valid_datetime_min")) {
                return ISODateTimeFormat.dateTimeNoMillis().parseDateTime(getJSON().getString("valid_datetime_min")).toDateTime().toDate().getTime();
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Long getValid_datetime_max() {
        try {
            if (!getJSON().isNull("valid_datetime_max")) {
                return ISODateTimeFormat.dateTimeNoMillis().parseDateTime(getJSON().getString("valid_datetime_max")).toDateTime().toDate().getTime();
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public QuestionType getType() {
        try {
            return QuestionType.valueOf(getJSON().getString("type"));
        } catch (JSONException | IllegalArgumentException e) {
            return QuestionType.T;
        }
    }

    @Override
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
}
