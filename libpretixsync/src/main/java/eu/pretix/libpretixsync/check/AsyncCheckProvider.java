package eu.pretix.libpretixsync.check;

import eu.pretix.libpretixsync.db.*;
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

    public AsyncCheckProvider(ConfigStore config, BlockingEntityStore<Persistable> dataStore) {
        this.config = config;
        this.dataStore = dataStore;
        this.sentry = new DummySentryImplementation();
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
        return new CheckResult(CheckResult.Type.INVALID);

        /*
        List<Ticket> tickets = dataStore.select(Ticket.class)
                .where(Ticket.SECRET.eq(ticketid))
                .get().toList();

        if (tickets.size() == 0) {
            return new CheckResult(CheckResult.Type.INVALID);
        }

        Ticket ticket = tickets.get(0);

        List<Item> items = dataStore.select(Item.class).where(Item.SERVER_ID.eq(ticket.getItem_id())).get().toList();
        List<Question> questions = new ArrayList<>();
        if (items.size() == 1) {
            Item item = items.get(0);
            questions = item.getQuestions();
        }

        CheckResult res = new CheckResult(CheckResult.Type.ERROR);
        res.setCheckinAllowed(ticket.isCheckin_allowed());

        long queuedCheckIns = dataStore.count(QueuedCheckIn.class)
                .where(QueuedCheckIn.SECRET.eq(ticketid))
                .get().value();

        if ((!ticket.isPaid() && !ignore_unpaid) || !ticket.isCheckin_allowed()) {
            res.setType(CheckResult.Type.UNPAID);
        } else if (ticket.isRedeemed() || queuedCheckIns > 0) {
            res.setType(CheckResult.Type.USED);
        } else {
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
                ticket.setRedeemed(true);
                dataStore.update(ticket);

                QueuedCheckIn qci = new QueuedCheckIn();
                qci.generateNonce();
                qci.setSecret(ticketid);
                qci.setDatetime(new Date());
                qci.setAnswers(givenAnswers.toString());
                dataStore.insert(qci);
            }
        }

        res.setTicket(ticket.getItem());
        res.setVariation(ticket.getVariation());
        res.setAttendee_name(ticket.getAttendee_name());
        res.setOrderCode(ticket.getOrder());
        res.setRequireAttention(ticket.isRequire_attention());
        res.setCheckinAllowed(ticket.isCheckin_allowed());

        return res;
        */
    }

    @Override
    public List<SearchResult> search(String query) throws CheckException {
        sentry.addBreadcrumb("provider.search", "offline search started");

        List<SearchResult> results = new ArrayList<>();
        /*

        if (query.length() < 4) {
            return results;
        }


        List<Ticket> tickets;
        if (config.getAllowSearch()) {
            tickets = dataStore.select(Ticket.class)
                    .where(
                            Ticket.SECRET.like(query + "%")
                                    .or(Ticket.ATTENDEE_NAME.like("%" + query + "%"))
                                    .or(Ticket.ORDER.like(query + "%"))
                    )
                    .limit(25)
                    .get().toList();
        } else {
            tickets = dataStore.select(Ticket.class)
                    .where(
                            Ticket.SECRET.like(query + "%")
                    )
                    .limit(25)
                    .get().toList();
        }

        for (Ticket ticket : tickets) {
            SearchResult sr = new SearchResult();
            sr.setTicket(ticket.getItem());
            sr.setVariation(ticket.getVariation());
            sr.setAttendee_name(ticket.getAttendee_name());
            sr.setOrderCode(ticket.getOrder());
            sr.setSecret(ticket.getSecret());
            sr.setRedeemed(ticket.isRedeemed());
            sr.setPaid(ticket.isPaid());
            sr.setRequireAttention(ticket.isRequire_attention());
            results.add(sr);
        }
        */
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
            if (resultItem.getVariations().size() > 0) {
                for (StatusResultItemVariation itemVariation : resultItem.getVariations()) {
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
