package eu.pretix.libpretixsync.check;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import eu.pretix.libpretixsync.DummySentryImplementation;
import eu.pretix.libpretixsync.SentryInterface;
import eu.pretix.libpretixsync.config.ConfigStore;
import eu.pretix.libpretixsync.db.AbstractQuestion;
import eu.pretix.libpretixsync.db.CheckIn;
import eu.pretix.libpretixsync.db.CheckInList;
import eu.pretix.libpretixsync.db.CheckInList_Item;
import eu.pretix.libpretixsync.db.Item;
import eu.pretix.libpretixsync.db.ItemVariation;
import eu.pretix.libpretixsync.db.Order;
import eu.pretix.libpretixsync.db.OrderPosition;
import eu.pretix.libpretixsync.db.Question;
import eu.pretix.libpretixsync.db.QueuedCheckIn;
import io.requery.BlockingEntityStore;
import io.requery.Persistable;
import io.requery.query.Condition;
import io.requery.query.Result;
import io.requery.query.Scalar;
import io.requery.query.WhereAndOr;

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
        return check(ticketid, new ArrayList<Answer>(), false, true);
    }

    @Override
    public CheckResult check(String ticketid, List<Answer> answers, boolean ignore_unpaid, boolean with_badge_data) {
        sentry.addBreadcrumb("provider.check", "offline check started");

        List<OrderPosition> tickets = dataStore.select(OrderPosition.class)
                .leftJoin(Order.class).on(Order.ID.eq(OrderPosition.ORDER_ID))
                .where(OrderPosition.SECRET.eq(ticketid))
                .and(Order.EVENT_SLUG.eq(config.getEventSlug()))
                .get().toList();

        if (tickets.size() == 0) {
            return new CheckResult(CheckResult.Type.INVALID);
        }

        OrderPosition position = tickets.get(0);

        Item item = position.getItem();
        Order order = position.getOrder();

        CheckInList list = dataStore.select(CheckInList.class)
                .where(CheckInList.SERVER_ID.eq(listId))
                .and(CheckInList.EVENT_SLUG.eq(config.getEventSlug()))
                .get().firstOrNull();
        if (list == null) {
            return new CheckResult(CheckResult.Type.ERROR, "Check-in list not found");
        }
        if (list.getSubevent_id() != null && list.getSubevent_id() > 0 && !list.getSubevent_id().equals(position.getSubeventId())) {
            return new CheckResult(CheckResult.Type.INVALID);
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
        QueuedCheckIn queuedCheckIns = dataStore.select(QueuedCheckIn.class)
                .where(QueuedCheckIn.SECRET.eq(ticketid))
                .and(QueuedCheckIn.CHECKIN_LIST_ID.eq(listId))
                .orderBy(QueuedCheckIn.DATETIME)
                .get().firstOrNull();

        boolean is_checked_in = false;
        if (queuedCheckIns != null) {
            is_checked_in = true;
            res.setFirstScanned(queuedCheckIns.getDatetime());
        } else {
            for (CheckIn ci : position.getCheckins()) {
                if (ci.getList().getServer_id().equals(listId)) {
                    is_checked_in = true;
                    res.setFirstScanned(ci.getDatetime());
                    break;
                }
            }
        }

        JSONObject jPosition;
        try {
            jPosition = position.getJSON();
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
            if (answers != null) {
                for (Answer a : answers) {
                    answerMap.put(a.getQuestion().getServer_id(), a.getValue());
                }
            }
            JSONArray givenAnswers = new JSONArray();
            List<RequiredAnswer> required_answers = new ArrayList<>();
            boolean ask_questions = false;
            for (Question q : questions) {
                if (!q.isAskDuringCheckin()) {
                    continue;
                }
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
                qci.setEvent_slug(config.getEventSlug());
                qci.setCheckinListId(listId);
                dataStore.insert(qci);

                CheckIn ci = new CheckIn();
                ci.setList(list);
                ci.setPosition(position);
                ci.setDatetime(new Date());
                ci.setJson_data("{\"local\": true}");
                dataStore.insert(ci);
            }
        }

        res.setTicket(position.getItem().getName());
        Long varid = position.getVariationId();
        if (varid != null) {
            try {
                ItemVariation var = item.getVariation(varid);
                if (var != null) {
                    res.setVariation(var.getStringValue());
                }
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
    public List<SearchResult> search(String query, int page) throws CheckException {
        sentry.addBreadcrumb("provider.search", "offline search started");

        List<SearchResult> results = new ArrayList<>();
        if (query.length() < 4) {
            return results;
        }

        CheckInList list = dataStore.select(CheckInList.class)
                .where(CheckInList.SERVER_ID.eq(listId))
                .and(CheckInList.EVENT_SLUG.eq(config.getEventSlug()))
                .get().firstOrNull();
        if (list == null) {
            throw new CheckException("Check-in list not found");
        }

        List<OrderPosition> positions;
        Condition search = null;
        query = query.toUpperCase();
        search = OrderPosition.SECRET.upper().like(query + "%")
                .or(OrderPosition.ATTENDEE_NAME.upper().like("%" + query + "%"))
                .or(OrderPosition.ATTENDEE_EMAIL.upper().like("%" + query + "%"))
                .or(Order.EMAIL.upper().like("%" + query + "%"))
                .or(Order.CODE.upper().like(query + "%"));
        if (!list.all_items) {
            List<Long> itemids = new ArrayList<>();
            for (Item item : list.getItems()) {
                itemids.add(item.getId());
            }
            search = Item.ID.in(itemids).and(search);
        }
        search = Order.EVENT_SLUG.eq(config.getEventSlug()).and(search);
        if (list.getSubevent_id() != null && list.getSubevent_id() > 0) {
            search = OrderPosition.SUBEVENT_ID.eq(list.getSubevent_id()).and(search);
        }
        // The weird typecasting is apparently due to a bug in the Java compiler
        // see https://github.com/requery/requery/issues/229#issuecomment-240470748
        positions = ((Result<OrderPosition>) dataStore.select(OrderPosition.class)
                .leftJoin(Order.class).on((Condition) Order.ID.eq(OrderPosition.ORDER_ID))
                .leftJoin(Item.class).on(Item.ID.eq(OrderPosition.ITEM_ID))
                .where(search).limit(50).offset(50 * (page - 1)).get()).toList();
        // TODO: search invoice_address?

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

            long queuedCheckIns = dataStore.count(QueuedCheckIn.class)
                    .where(QueuedCheckIn.SECRET.eq(position.getSecret()))
                    .and(QueuedCheckIn.CHECKIN_LIST_ID.eq(listId))
                    .get().value();

            boolean is_checked_in = queuedCheckIns > 0;
            for (CheckIn ci : position.getCheckins()) {
                if (ci.getList().getServer_id().equals(listId)) {
                    is_checked_in = true;
                    break;
                }
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

    private WhereAndOr<? extends Scalar<Integer>> basePositionQuery(CheckInList list) {
        List<String> status = new ArrayList<>();
        status.add("p");
        if (list.include_pending) {
            status.add("n");
        }

        WhereAndOr<? extends Scalar<Integer>> q = dataStore.count(OrderPosition.class).distinct()
                .leftJoin(Order.class).on(OrderPosition.ORDER_ID.eq(Order.ID))
                .where(Order.EVENT_SLUG.eq(config.getEventSlug()))
                .and(Order.STATUS.in(status));
        if (list.getSubevent_id() != null && list.getSubevent_id() > 0) {
            q = q.and(OrderPosition.SUBEVENT_ID.eq(list.getSubevent_id()));
        }
        return q;
    }

    private WhereAndOr<? extends Scalar<Integer>> baseCheckInQuery(CheckInList list) {
        List<String> status = new ArrayList<>();
        status.add("p");
        if (list.include_pending) {
            status.add("n");
        }

        WhereAndOr<? extends Scalar<Integer>> q = dataStore.count(CheckIn.class).distinct()
                .leftJoin(OrderPosition.class).on(CheckIn.POSITION_ID.eq(OrderPosition.ID))
                .leftJoin(Order.class).on(OrderPosition.ORDER_ID.eq(Order.ID))
                .where(Order.EVENT_SLUG.eq(config.getEventSlug()))
                .and(CheckIn.LIST_ID.eq(list.getId()))
                .and(Order.STATUS.in(status));
        if (list.getSubevent_id() != null && list.getSubevent_id() > 0) {
            q = q.and(OrderPosition.SUBEVENT_ID.eq(list.getSubevent_id()));
        }
        return q;
    }

    @Override
    public StatusResult status() throws CheckException {
        sentry.addBreadcrumb("provider.status", "offline status started");

        List<StatusResultItem> items = new ArrayList<>();

        CheckInList list = dataStore.select(CheckInList.class)
                .where(CheckInList.SERVER_ID.eq(listId))
                .and(CheckInList.EVENT_SLUG.eq(config.getEventSlug()))
                .get().firstOrNull();
        if (list == null) {
            throw new CheckException("Check-in list not found");
        }

        List<Item> products;
        if (list.all_items) {
            products = dataStore.select(Item.class)
                    .where(Item.EVENT_SLUG.eq(config.getEventSlug()))
                    .get().toList();
        } else {
            products = list.getItems();
        }

        int sum_pos = 0;
        int sum_ci = 0;
        for (Item product : products) {
            List<StatusResultItemVariation> variations = new ArrayList<>();

            try {
                for (ItemVariation var : product.getVariations()) {
                    int position_count = basePositionQuery(list)
                            .and(OrderPosition.ITEM_ID.eq(product.id))
                            .and(OrderPosition.VARIATION_ID.eq(var.getServer_id())).get().value();
                    int ci_count = baseCheckInQuery(list)
                            .and(OrderPosition.ITEM_ID.eq(product.id))
                            .and(OrderPosition.VARIATION_ID.eq(var.getServer_id())).get().value();
                    variations.add(new StatusResultItemVariation(
                            var.getServer_id(),
                            var.getStringValue(),
                            position_count,
                            ci_count
                    ));
                }
                int position_count = basePositionQuery(list)
                        .and(OrderPosition.ITEM_ID.eq(product.id)).get().value();
                int ci_count = baseCheckInQuery(list)
                        .and(OrderPosition.ITEM_ID.eq(product.id)).get().value();
                items.add(new StatusResultItem(
                        product.getServer_id(),
                        product.getName(),
                        position_count,
                        ci_count,
                        variations,
                        product.isAdmission()
                ));
                sum_pos += position_count;
                sum_ci += ci_count;
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }


        StatusResult statusResult = new StatusResult(list.name, sum_pos, sum_ci, items);
        return statusResult;
    }
}
