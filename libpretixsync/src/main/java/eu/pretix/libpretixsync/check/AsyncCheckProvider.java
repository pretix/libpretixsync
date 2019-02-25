package eu.pretix.libpretixsync.check;

import eu.pretix.libpretixsync.db.*;
import io.requery.kotlin.Logical;
import io.requery.query.Condition;
import io.requery.query.Expression;
import io.requery.query.Result;
import io.requery.query.WhereAndOr;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;

import eu.pretix.libpretixsync.DummySentryImplementation;
import eu.pretix.libpretixsync.SentryInterface;
import eu.pretix.libpretixsync.config.ConfigStore;
import io.requery.BlockingEntityStore;
import io.requery.Persistable;

public class AsyncCheckProvider implements TicketCheckProvider {
    private ConfigStore config;
    private BlockingEntityStore<Persistable> dataStore;
    private SentryInterface sentry;
    private Long listId;

    public AsyncCheckProvider(ConfigStore config, BlockingEntityStore<Persistable> dataStore, Long listId) {
        this.config = config;
        this.dataStore = dataStore;
        this.sentry = new DummySentryImplementation();
        this.listId = listId;
    }

    public SentryInterface getSentry() {
        return sentry;
    }

    public void setSentry(SentryInterface sentry) {
        this.sentry = sentry;
    }

    @Override
    public CheckResult check(String ticketid) {
        return check(ticketid, new ArrayList<Answer>(), false);
    }

    @Override
    public CheckResult check(String ticketid, List<Answer> answers, boolean ignore_unpaid) {
        sentry.addBreadcrumb("provider.check", "offline check started");

        List<OrderPosition> tickets = dataStore.select(OrderPosition.class)
                .where(OrderPosition.SECRET.eq(ticketid))
                .get().toList();

        if (tickets.size() == 0) {
            return new CheckResult(CheckResult.Type.INVALID);
        }

        OrderPosition position = tickets.get(0);

        Item item = position.getItem();
        Order order = position.getOrder();

        CheckInList list = dataStore.select(CheckInList.class)
                .where(CheckInList.SERVER_ID.eq(listId))
                .get().firstOrNull();
        if (list == null) {
            return new CheckResult(CheckResult.Type.ERROR, "Check-in list not found");
        }
        if (!list.all_items) {
            int is_in_list = dataStore.count(CheckInList_Item.class)
                    .where(CheckInList_Item.ITEM_ID.eq(item.getId()))
                    .and(CheckInList_Item.CHECK_IN_LIST_ID.eq(list.getId()))
                    .get().value();
            if (is_in_list == 0) {
                return new CheckResult(CheckResult.Type.PRODUCT);
            }
        }

        CheckResult res = new CheckResult(CheckResult.Type.ERROR);
        long queuedCheckIns = dataStore.count(QueuedCheckIn.class)
                .where(QueuedCheckIn.SECRET.eq(ticketid))
                .and(QueuedCheckIn.CHECKIN_LIST_ID.eq(listId))
                .get().value();

        boolean is_checked_in = queuedCheckIns > 0;
        JSONObject jPosition;
        try {
            jPosition = position.getJSON();
            for (int i = 0; i < jPosition.getJSONArray("checkins").length(); i++) {
                JSONObject c = jPosition.getJSONArray("checkins").getJSONObject(i);
                if (c.getLong("list") == listId) {
                    is_checked_in = true;
                    break;
                }
            }
        } catch (JSONException e) {
            sentry.captureException(e);
            return new CheckResult(CheckResult.Type.ERROR);
        }

        if (order.getStatus().equals("p")) {
            res.setCheckinAllowed(true);
        } else if (order.getStatus().equals("n")) {
            res.setCheckinAllowed(list.include_pending);
        } else {
            res.setCheckinAllowed(false);
        }

        if ((!order.getStatus().equals("p") && !ignore_unpaid)) {
            res.setType(CheckResult.Type.UNPAID);
        } else if (is_checked_in) {
            res.setType(CheckResult.Type.USED);
        } else {
            List<Question> questions = item.getQuestions();
            Map<Long, String> answerMap = new HashMap<>();
            for (Answer a : answers) {
                answerMap.put(a.getQuestion().getServer_id(), a.getValue());
            }
            JSONArray givenAnswers = new JSONArray();
            List<RequiredAnswer> required_answers = new ArrayList<>();
            boolean ask_questions = false;
            for (Question q : questions) {
                String answer = "";
                if (answerMap.containsKey(q.getServer_id())) {
                    answer = answerMap.get(q.getServer_id());
                    try {
                        answer = q.clean_answer(answer, q.getOptions());
                        JSONObject jo = new JSONObject();
                        jo.put("answer", answer);
                        jo.put("question", q.getServer_id());
                        givenAnswers.put(jo);
                    } catch (AbstractQuestion.ValidationException | JSONException e) {
                        answer = "";
                        ask_questions = true;
                    }
                } else {
                    ask_questions = true;
                }
                required_answers.add(new RequiredAnswer(q, answer));
            }

            if (ask_questions && required_answers.size() > 0) {
                res.setType(CheckResult.Type.ANSWERS_REQUIRED);
                res.setRequiredAnswers(required_answers);
            } else {
                res.setType(CheckResult.Type.VALID);

                QueuedCheckIn qci = new QueuedCheckIn();
                qci.generateNonce();
                qci.setSecret(ticketid);
                qci.setDatetime(new Date());
                qci.setAnswers(givenAnswers.toString());
                qci.setCheckinListId(listId);
                dataStore.insert(qci);

                try {
                    JSONArray c = jPosition.getJSONArray("checkins");
                    JSONObject cj = new JSONObject();
                    cj.put("list", listId);
                    cj.put("local", true);
                    c.put(cj);
                    jPosition.put("checkins", c);
                    position.setJson_data(jPosition.toString());
                    dataStore.update(position);
                } catch (JSONException e) {
                    sentry.captureException(e);
                }
            }
        }

        res.setTicket(position.getItem().getName());
        Long varid = position.getVariationId();
        if (varid != null) {
            try {
                ItemVariation var = item.getVariation(varid);
                res.setVariation(var.getStringValue());
            } catch (JSONException e) {
                sentry.captureException(e);
            }

        }

        res.setAttendee_name(position.attendee_name);
        res.setOrderCode(position.getOrder().getCode());
        res.setPosition(jPosition);
        boolean require_attention = position.getOrder().isCheckin_attention();
        try {
            require_attention = require_attention || item.getJSON().optBoolean("checkin_attention", false);
        } catch (JSONException e) {
            sentry.captureException(e);
        }
        res.setRequireAttention(require_attention);
        return res;
    }

    @Override
    public List<SearchResult> search(String query) throws CheckException {
        sentry.addBreadcrumb("provider.search", "offline search started");

        List<SearchResult> results = new ArrayList<>();
        if (query.length() < 4) {
            return results;
        }

        CheckInList list = dataStore.select(CheckInList.class)
                .where(CheckInList.SERVER_ID.eq(listId))
                .get().firstOrNull();
        if (list == null) {
            throw new CheckException("Check-in list not found");
        }

        List<OrderPosition> positions;
        Condition search = null;
        if (config.getAllowSearch()) {
            search = OrderPosition.SECRET.like(query + "%")
                            .or(OrderPosition.ATTENDEE_NAME.like("%" + query + "%"))
                            .or(Order.CODE.like(query + "%"));
        } else {
            search = OrderPosition.SECRET.like(query + "%");
        }
        if (!list.all_items) {
            List<Long> itemids = new ArrayList<>();
            for (Item item : list.getItems()) {
                itemids.add(item.getId());
            }
            search = Item.ID.in(itemids).and(search);
        }
        // The weird typecasting is apparently due to a bug in the Java compiler
        // see https://github.com/requery/requery/issues/229#issuecomment-240470748
        positions = ((Result<OrderPosition>) dataStore.select(OrderPosition.class)
                .leftJoin(Order.class).on((Condition) Order.ID.eq(OrderPosition.ORDER_ID))
                .leftJoin(Item.class).on(Item.ID.eq(OrderPosition.ITEM_ID))
                .where(search).limit(25).get()).toList();
        // TODO: search addon_to and invoice_address?

        for (OrderPosition position : positions) {
            Item item = position.getItem();
            Order order = position.getOrder();

            SearchResult sr = new SearchResult();
            sr.setTicket(item.getName());
            try {
                if (position.getVariationId() != null && position.getVariationId() > 0) {
                    sr.setVariation(item.getVariation(position.getVariationId()).getStringValue());
                }
            } catch (JSONException e) {
                sentry.captureException(e);
            }
            sr.setAttendee_name(position.attendee_name);
            sr.setOrderCode(order.getCode());
            sr.setSecret(position.getSecret());

            CheckResult res = new CheckResult(CheckResult.Type.ERROR);
            long queuedCheckIns = dataStore.count(QueuedCheckIn.class)
                    .where(QueuedCheckIn.SECRET.eq(position.getSecret()))
                    .and(QueuedCheckIn.CHECKIN_LIST_ID.eq(listId))
                    .get().value();

            boolean is_checked_in = queuedCheckIns > 0;
            JSONObject jPosition;
            try {
                jPosition = position.getJSON();
                for (int i = 0; i < jPosition.getJSONArray("checkins").length(); i++) {
                    JSONObject c = jPosition.getJSONArray("checkins").getJSONObject(i);
                    if (c.getLong("list") == listId) {
                        is_checked_in = true;
                        break;
                    }
                }
            } catch (JSONException e) {
                sentry.captureException(e);
            }

            sr.setRedeemed(is_checked_in);
            sr.setPaid(order.getStatus().equals("p"));
            boolean require_attention = position.getOrder().isCheckin_attention();
            try {
                require_attention = require_attention || item.getJSON().optBoolean("checkin_attention", false);
            } catch (JSONException e) {
                sentry.captureException(e);
            }
            sr.setRequireAttention(require_attention);
            results.add(sr);
        }
        return results;
    }

    @Override
    public StatusResult status() throws CheckException {
        sentry.addBreadcrumb("provider.status", "offline status started");
        if (config.getLastStatusData() == null) {
            throw new CheckException("No current data available.");
        }
        StatusResult statusResult;
        try {
            statusResult = OnlineCheckProvider.parseStatusResponse(new JSONObject(config.getLastStatusData()));
        } catch (JSONException e) {
            e.printStackTrace();
            throw new CheckException("Invalid status data available.");
        }
        /*

        if (dataStore.count(Ticket.class).where(Ticket.ITEM_ID.eq((long) 0)).get().value() > 0) {
            throw new CheckException("Incompatible with your current pretix version.");
        }
        int total_all = 0;
        int checkins_all = 0;
        for (StatusResultItem resultItem : statusResult.getItems()) {
            int total = 0;
            int checkins = 0;
            if (resultItem.getActiveVariations().size() > 0) {
                for (StatusResultItemVariation itemVariation : resultItem.getActiveVariations()) {
                    itemVariation.setTotal(
                            dataStore.count(Ticket.class).where(
                                    Ticket.ITEM_ID.eq(resultItem.getId())
                                            .and(Ticket.VARIATION_ID.eq(itemVariation.getId()))
                                            .and(Ticket.PAID.eq(true))
                            ).get().value()
                    );
                    itemVariation.setCheckins(
                            dataStore.count(Ticket.class).where(
                                    Ticket.ITEM_ID.eq(resultItem.getId())
                                            .and(Ticket.VARIATION_ID.eq(itemVariation.getId()))
                                            .and(Ticket.REDEEMED.eq(true))
                                            .and(Ticket.PAID.eq(true))
                            ).get().value()
                    );
                    total += itemVariation.getTotal();
                    checkins += itemVariation.getCheckins();
                }
            } else {
                total = dataStore.count(Ticket.class).where(
                        Ticket.ITEM_ID.eq(resultItem.getId())
                                .and(Ticket.PAID.eq(true))
                ).get().value();
                checkins = dataStore.count(Ticket.class).where(
                        Ticket.ITEM_ID.eq(resultItem.getId())
                                .and(Ticket.REDEEMED.eq(true))
                                .and(Ticket.PAID.eq(true))
                ).get().value();
            }
            resultItem.setTotal(total);
            resultItem.setCheckins(checkins);
            total_all += total;
            checkins_all += checkins;
        }
        statusResult.setAlreadyScanned(checkins_all);
        statusResult.setTotalTickets(total_all);
        */
        return statusResult;
    }
}
