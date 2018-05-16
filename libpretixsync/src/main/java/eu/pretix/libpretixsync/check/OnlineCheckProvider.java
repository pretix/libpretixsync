package eu.pretix.libpretixsync.check;


import eu.pretix.libpretixsync.db.Question;
import eu.pretix.libpretixsync.db.QuestionOption;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import eu.pretix.libpretixsync.DummySentryImplementation;
import eu.pretix.libpretixsync.SentryInterface;
import eu.pretix.libpretixsync.api.ApiException;
import eu.pretix.libpretixsync.api.DefaultHttpClientFactory;
import eu.pretix.libpretixsync.api.HttpClientFactory;
import eu.pretix.libpretixsync.api.PretixApi;
import eu.pretix.libpretixsync.config.ConfigStore;

public class OnlineCheckProvider implements TicketCheckProvider {
    protected PretixApi api;
    private ConfigStore config;
    private SentryInterface sentry;

    public OnlineCheckProvider(ConfigStore config, HttpClientFactory httpClientFactory) {
        this.config = config;
        this.api = PretixApi.fromConfig(config, httpClientFactory);
        this.sentry = new DummySentryImplementation();
    }

    public OnlineCheckProvider(ConfigStore config) {
        this(config, new DefaultHttpClientFactory());
    }

    public SentryInterface getSentry() {
        return sentry;
    }

    public void setSentry(SentryInterface sentry) {
        this.sentry = sentry;
        this.api.setSentry(sentry);
    }

    @Override
    public CheckResult check(String ticketid, List<Answer> answers, boolean ignore_unpaid) {
        sentry.addBreadcrumb("provider.check", "started");
        try {
            CheckResult res = new CheckResult(CheckResult.Type.ERROR);
            JSONObject response = api.redeem(ticketid, answers, ignore_unpaid);
            String status = response.getString("status");
            if ("ok".equals(status)) {
                res.setType(CheckResult.Type.VALID);
            } else if ("incomplete".equals(status)) {
                res.setType(CheckResult.Type.ANSWERS_REQUIRED);
                List<RequiredAnswer> required_answers = new ArrayList<>();
                for (int i = 0; i < response.getJSONArray("questions").length(); i++) {
                    JSONObject q = response.getJSONArray("questions").getJSONObject(i);
                    Question question = new Question();
                    question.setJson_data(q.toString());
                    required_answers.add(new RequiredAnswer(question, ""));
                }
                res.setRequiredAnswers(required_answers);
            } else {
                String reason = response.optString("reason");
                if ("already_redeemed".equals(reason)) {
                    res.setType(CheckResult.Type.USED);
                } else if ("unknown_ticket".equals(reason)) {
                    res.setType(CheckResult.Type.INVALID);
                } else if ("unpaid".equals(reason)) {
                    res.setType(CheckResult.Type.UNPAID);
                } else if ("product".equals(reason)) {
                    res.setType(CheckResult.Type.PRODUCT);
                }
            }

            if (response.has("data")) {
                res.setTicket(response.getJSONObject("data").getString("item"));
                res.setVariation(response.getJSONObject("data").getString("variation"));
                res.setAttendee_name(response.getJSONObject("data").getString("attendee_name"));
                res.setOrderCode(response.getJSONObject("data").getString("order"));
                res.setRequireAttention(response.getJSONObject("data").optBoolean("attention", false));
                res.setCheckinAllowed(response.getJSONObject("data").optBoolean("checkin_allowed", res.getType() != CheckResult.Type.UNPAID));
            }
            return res;
        } catch (JSONException e) {
            sentry.captureException(e);
            CheckResult cr = new CheckResult(CheckResult.Type.ERROR, "Invalid server response");
            if (e.getCause() != null)
                cr.setTicket(e.getCause().getMessage());
            return cr;
        } catch (ApiException e) {
            sentry.addBreadcrumb("provider.check", "API Error: " + e.getMessage());
            CheckResult cr = new CheckResult(CheckResult.Type.ERROR, e.getMessage());
            if (e.getCause() != null)
                cr.setTicket(e.getCause().getMessage());
            return cr;
        }
    }

    @Override
    public CheckResult check(String ticketid) {
        return check(ticketid, new ArrayList<Answer>(), false);
    }

    @Override
    public List<SearchResult> search(String query) throws CheckException {
        sentry.addBreadcrumb("provider.search", "started");
        try {
            JSONObject response = api.search(query);

            List<SearchResult> results = new ArrayList<>();
            for (int i = 0; i < response.getJSONArray("results").length(); i++) {
                JSONObject res = response.getJSONArray("results").getJSONObject(i);
                SearchResult sr = new SearchResult();
                sr.setAttendee_name(res.getString("attendee_name"));
                sr.setTicket(res.getString("item"));
                sr.setVariation(res.getString("variation"));
                sr.setOrderCode(res.getString("order"));
                sr.setSecret(res.getString("secret"));
                sr.setRedeemed(res.getBoolean("redeemed"));
                sr.setPaid(res.getBoolean("paid"));
                sr.setRequireAttention(res.optBoolean("attention", false));
                results.add(sr);
            }
            return results;
        } catch (JSONException e) {
            sentry.captureException(e);
            throw new CheckException("Unknown server response");
        } catch (ApiException e) {
            sentry.addBreadcrumb("provider.search", "API Error: " + e.getMessage());
            throw new CheckException(e.getMessage());
        }
    }

    public static StatusResult parseStatusResponse(JSONObject response) throws JSONException {
        List<StatusResultItem> items = new ArrayList<>();

        int itemcount = response.getJSONArray("items").length();
        for (int i = 0; i < itemcount; i++) {
            JSONObject item = response.getJSONArray("items").getJSONObject(i);
            List<StatusResultItemVariation> variations = new ArrayList<>();

            int varcount = item.getJSONArray("variations").length();
            for (int j = 0; j < varcount; j++) {
                JSONObject var = item.getJSONArray("variations").getJSONObject(j);
                variations.add(new StatusResultItemVariation(
                        var.getLong("id"),
                        var.getString("name"),
                        var.getInt("total"),
                        var.getInt("checkins")
                ));
            }

            items.add(new StatusResultItem(
                    item.getLong("id"),
                    item.getString("name"),
                    item.getInt("total"),
                    item.getInt("checkins"),
                    variations,
                    item.getBoolean("admission")
            ));
        }

        return new StatusResult(
                response.getJSONObject("event").getString("name"),
                response.getInt("total"),
                response.getInt("checkins"),
                items
        );
    }

    @Override
    public StatusResult status() throws CheckException {
        sentry.addBreadcrumb("provider.status", "started");
        try {
            JSONObject response = api.status();
            return parseStatusResponse(response);
        } catch (JSONException e) {
            sentry.captureException(e);
            throw new CheckException("Unknown server response");
        } catch (ApiException e) {
            sentry.addBreadcrumb("provider.search", "API Error: " + e.getMessage());
            throw new CheckException(e.getMessage());
        }
    }
}
