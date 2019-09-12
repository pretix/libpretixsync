package eu.pretix.libpretixsync.check;


import eu.pretix.libpretixsync.db.CheckIn;
import eu.pretix.libpretixsync.db.CheckInList;
import eu.pretix.libpretixsync.db.Item;
import eu.pretix.libpretixsync.db.ItemVariation;
import eu.pretix.libpretixsync.db.Question;
import eu.pretix.libpretixsync.db.QuestionOption;

import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
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
import io.requery.BlockingEntityStore;
import io.requery.Persistable;

public class OnlineCheckProvider implements TicketCheckProvider {
    protected PretixApi api;
    private ConfigStore config;
    private SentryInterface sentry;
    private BlockingEntityStore<Persistable> dataStore;
    private Long listId;
    private DateTimeFormatter parser = ISODateTimeFormat.dateTimeParser();

    public OnlineCheckProvider(ConfigStore config, HttpClientFactory httpClientFactory, BlockingEntityStore<Persistable> dataStore, Long listId) {
        this.config = config;
        this.api = PretixApi.fromConfig(config, httpClientFactory);
        this.sentry = new DummySentryImplementation();
        this.listId = listId;
        this.dataStore = dataStore;
    }

    public OnlineCheckProvider(ConfigStore config, BlockingEntityStore<Persistable> dataStore, Long listId) {
        this(config, new DefaultHttpClientFactory(), dataStore, listId);
    }

    public SentryInterface getSentry() {
        return sentry;
    }

    public void setSentry(SentryInterface sentry) {
        this.sentry = sentry;
        this.api.setSentry(sentry);
    }

    @Override
    public CheckResult check(String ticketid, List<Answer> answers, boolean ignore_unpaid, boolean with_badge_data) {
        sentry.addBreadcrumb("provider.check", "started");

        CheckInList list = dataStore.select(CheckInList.class)
                .where(CheckInList.SERVER_ID.eq(listId))
                .and(CheckInList.EVENT_SLUG.eq(config.getEventSlug()))
                .get().firstOrNull();
        if (list == null) {
            return new CheckResult(CheckResult.Type.ERROR, "Check-in list not found");
        }

        try {
            CheckResult res = new CheckResult(CheckResult.Type.ERROR);
            PretixApi.ApiResponse responseObj = api.redeem(ticketid, null, false, null, answers, listId, ignore_unpaid, with_badge_data);
            if (responseObj.getResponse().code() == 404) {
                res.setType(CheckResult.Type.INVALID);
            } else {
                JSONObject response = responseObj.getData();
                String status = response.getString("status");
                if ("ok".equals(status)) {
                    res.setType(CheckResult.Type.VALID);
                } else if ("incomplete".equals(status)) {
                    res.setType(CheckResult.Type.ANSWERS_REQUIRED);
                    List<RequiredAnswer> required_answers = new ArrayList<>();
                    for (int i = 0; i < response.getJSONArray("questions").length(); i++) {
                        JSONObject q = response.getJSONArray("questions").getJSONObject(i);
                        Question question = new Question();
                        question.setServer_id(q.getLong("id"));
                        question.setRequired(q.getBoolean("required"));
                        question.setPosition(q.getLong("position"));
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
                    } else if ("canceled".equals(reason)) {
                        res.setType(CheckResult.Type.CANCELED);
                    } else if ("unpaid".equals(reason)) {
                        res.setType(CheckResult.Type.UNPAID);

                        // Decide whether the user is allowed to "try again" with "ignore_unpaid"
                        res.setCheckinAllowed(list.include_pending && response.has("position") && response.getJSONObject("position").optString("order__status", "n").equals("n"));
                    } else if ("product".equals(reason)) {
                        res.setType(CheckResult.Type.PRODUCT);
                    }
                }

                if (response.has("position")) {
                    JSONObject posjson = response.getJSONObject("position");

                    Item item = dataStore.select(Item.class)
                            .where(Item.SERVER_ID.eq(posjson.getLong("item")))
                            .get().firstOrNull();
                    if (item != null) {
                        res.setTicket(item.getInternalName());
                        if (posjson.optLong("variation", 0) > 0) {
                            ItemVariation iv = item.getVariation(posjson.getLong("variation"));
                            if (iv != null) {
                                res.setVariation(iv.getStringValue());
                            }
                        }
                    }
                    if (!posjson.isNull("attendee_name")) {
                        res.setAttendee_name(posjson.optString("attendee_name"));
                    }
                    res.setOrderCode(posjson.optString("order"));
                    res.setPosition(posjson);

                    JSONArray checkins = posjson.getJSONArray("checkins");
                    for (int i = 0; i < checkins.length(); i++) {
                        JSONObject ci = checkins.getJSONObject(i);
                        if (ci.getLong("list") == listId) {
                            res.setFirstScanned(parser.parseDateTime(ci.getString("datetime")).toDate());
                        }
                    }
                }
                res.setRequireAttention(response.optBoolean("require_attention", false));
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
        return check(ticketid, new ArrayList<Answer>(), false, true);
    }

    @Override
    public List<SearchResult> search(String query, int page) throws CheckException {
        sentry.addBreadcrumb("provider.search", "started");
        try {
            PretixApi.ApiResponse response = api.search(listId, query, page);
            JSONArray resdata = response.getData().getJSONArray("results");

            List<SearchResult> results = new ArrayList<>();
            for (int i = 0; i < resdata.length(); i++) {
                JSONObject res = resdata.getJSONObject(i);
                SearchResult sr = new SearchResult();

                Item item = dataStore.select(Item.class)
                        .where(Item.SERVER_ID.eq(res.getLong("item")))
                        .get().firstOrNull();
                if (item != null) {
                    sr.setTicket(item.getInternalName());
                    if (res.optLong("variation", 0) > 0) {
                        ItemVariation iv = item.getVariation(res.getLong("variation"));
                        if (iv != null) {
                            sr.setVariation(iv.getStringValue());
                        }
                    }
                }
                if (!res.isNull("attendee_name")) {
                    sr.setAttendee_name(res.optString("attendee_name"));
                }
                sr.setOrderCode(res.optString("order"));
                sr.setSecret(res.optString("secret"));
                sr.setRedeemed(res.getJSONArray("checkins").length() > 0);
                String status = res.optString("order__status", "p");
                if (status.equals("p")) {
                    sr.setStatus(SearchResult.Status.PAID);
                } else if (status.equals("n")) {
                    sr.setStatus(SearchResult.Status.PENDING);
                } else {
                    sr.setStatus(SearchResult.Status.CANCELED);
                }
                sr.setRequireAttention(res.optBoolean("require_attention", false));
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
                        var.getString("value"),
                        var.getInt("position_count"),
                        var.getInt("checkin_count")
                ));
            }

            items.add(new StatusResultItem(
                    item.getLong("id"),
                    item.getString("name"),
                    item.getInt("position_count"),
                    item.getInt("checkin_count"),
                    variations,
                    item.getBoolean("admission")
            ));
        }

        return new StatusResult(
                response.getJSONObject("event").getString("name"),
                response.getInt("position_count"),
                response.getInt("checkin_count"),
                items
        );
    }

    @Override
    public StatusResult status() throws CheckException {
        sentry.addBreadcrumb("provider.status", "started");
        try {
            PretixApi.ApiResponse response = api.status(listId);
            return parseStatusResponse(response.getData());
        } catch (JSONException e) {
            sentry.captureException(e);
            throw new CheckException("Unknown server response");
        } catch (ApiException e) {
            sentry.addBreadcrumb("provider.search", "API Error: " + e.getMessage());
            throw new CheckException(e.getMessage());
        }
    }
}
