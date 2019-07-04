package eu.pretix.libpretixsync.check;


import java.util.Date;
import java.util.List;
import java.util.Objects;

import eu.pretix.libpretixsync.SentryInterface;
import eu.pretix.libpretixsync.db.Question;
import org.json.JSONObject;

public interface TicketCheckProvider {

    class RequiredAnswer {
        private Question question;
        private String current_value;

        public RequiredAnswer(Question question, String current_value) {
            this.question = question;
            this.current_value = current_value;
        }

        public Question getQuestion() {
            return question;
        }

        public String getCurrentValue() {
            return current_value;
        }
    }

    class Answer {
        private Question question;
        private String value;

        public Answer(Question question, String value) {
            this.question = question;
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public Question getQuestion() {
            return question;
        }
    }

    class CheckResult {
        public enum Type {
            INVALID, VALID, USED, ERROR, UNPAID, PRODUCT, ANSWERS_REQUIRED
        }

        private Type type;
        private String ticket;
        private String variation;
        private String attendee_name;
        private String message;
        private String order_code;
        private Date first_scanned;
        private String addon_text;
        private boolean require_attention;
        private boolean checkin_allowed;
        private List<RequiredAnswer> required_answers;
        private JSONObject position;

        public CheckResult(Type type, String message) {
            this.type = type;
            this.message = message;
        }

        public CheckResult(Type type) {
            this.type = type;
        }

        public Type getType() {
            return type;
        }

        public void setType(Type type) {
            this.type = type;
        }

        public String getTicket() {
            return ticket;
        }

        public void setTicket(String ticket) {
            this.ticket = ticket;
        }

        public String getVariation() {
            return variation;
        }

        public void setVariation(String variation) {
            this.variation = variation;
        }

        public String getAttendee_name() {
            return attendee_name;
        }

        public void setAttendee_name(String attendee_name) {
            this.attendee_name = attendee_name;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getOrderCode() {
            return order_code;
        }

        public void setOrderCode(String order_code) {
            this.order_code = order_code;
        }

        public String getAddonText() {
            return addon_text;
        }

        public void setAddonText(String addon_text) {
            this.addon_text = addon_text;
        }

        public boolean isRequireAttention() {
            return require_attention;
        }

        public void setRequireAttention(boolean require_attention) {
            this.require_attention = require_attention;
        }

        public List<RequiredAnswer> getRequiredAnswers() {
            return required_answers;
        }

        public void setRequiredAnswers(List<RequiredAnswer> required_answers) {
            this.required_answers = required_answers;
        }

        public boolean isCheckinAllowed() {
            return checkin_allowed;
        }

        public void setCheckinAllowed(boolean checkin_allowed) {
            this.checkin_allowed = checkin_allowed;
        }

        public Date getFirstScanned() {
            return first_scanned;
        }

        public void setFirstScanned(Date first_scanned) {
            this.first_scanned = first_scanned;
        }

        public JSONObject getPosition() {
            return position;
        }

        public void setPosition(JSONObject position) {
            this.position = position;
        }
    }

    class SearchResult {

        private String secret;
        private String ticket;
        private String variation;
        private String attendee_name;
        private String order_code;
        private String addon_text;
        private boolean paid;
        private boolean redeemed;
        private boolean require_attention;

        public SearchResult() {
        }

        public SearchResult(SearchResult r) {
            secret = r.secret;
            ticket = r.ticket;
            variation = r.variation;
            attendee_name = r.attendee_name;
            order_code = r.order_code;
            paid = r.paid;
            redeemed = r.redeemed;
            require_attention = r.require_attention;
            addon_text = r.addon_text;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public boolean isPaid() {
            return paid;
        }

        public void setPaid(boolean paid) {
            this.paid = paid;
        }

        public boolean isRedeemed() {
            return redeemed;
        }

        public void setRedeemed(boolean redeemed) {
            this.redeemed = redeemed;
        }

        public String getTicket() {
            return ticket;
        }

        public void setTicket(String ticket) {
            this.ticket = ticket;
        }

        public String getVariation() {
            return variation;
        }

        public void setVariation(String variation) {
            this.variation = variation;
        }

        public String getAttendee_name() {
            return attendee_name;
        }

        public void setAttendee_name(String attendee_name) {
            this.attendee_name = attendee_name;
        }

        public String getOrderCode() {
            return order_code;
        }

        public void setOrderCode(String order_code) {
            this.order_code = order_code;
        }

        public String getAddonText() {
            return addon_text;
        }

        public void setAddonText(String addon_text) {
            this.addon_text = addon_text;
        }

        public boolean isRequireAttention() {
            return require_attention;
        }

        public void setRequireAttention(boolean require_attention) {
            this.require_attention = require_attention;
        }
    }

    class StatusResultItemVariation {
        private long id;
        private String name;
        private int checkins;
        private int total;

        public StatusResultItemVariation(long id, String name, int total, int checkins) {
            this.name = name;
            this.checkins = checkins;
            this.total = total;
            this.id = id;
        }

        public long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public int getCheckins() {
            return checkins;
        }

        public int getTotal() {
            return total;
        }

        public void setCheckins(int checkins) {
            this.checkins = checkins;
        }

        public void setTotal(int total) {
            this.total = total;
        }
    }

    class StatusResultItem {
        private long id;
        private String name;
        private int checkins;
        private int total;
        private List<StatusResultItemVariation> variations;
        private boolean admission;

        public StatusResultItem(long id, String name, int total, int checkins, List<StatusResultItemVariation> variations, boolean admission) {
            this.name = name;
            this.checkins = checkins;
            this.total = total;
            this.variations = variations;
            this.admission = admission;
            this.id = id;
        }

        public long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public int getCheckins() {
            return checkins;
        }

        public int getTotal() {
            return total;
        }

        public boolean isAdmission() {
            return admission;
        }

        public List<StatusResultItemVariation> getVariations() {
            return variations;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setCheckins(int checkins) {
            this.checkins = checkins;
        }

        public void setTotal(int total) {
            this.total = total;
        }
    }

    class StatusResult {
        private String eventName;
        private int totalTickets;
        private int alreadyScanned;
        private List<StatusResultItem> items;

        public StatusResult(String eventName, int totalTickets, int alreadyScanned, List<StatusResultItem> items) {
            this.eventName = eventName;
            this.totalTickets = totalTickets;
            this.alreadyScanned = alreadyScanned;
            this.items = items;
        }

        public String getEventName() {
            return eventName;
        }

        public int getTotalTickets() {
            return totalTickets;
        }

        public int getAlreadyScanned() {
            return alreadyScanned;
        }

        public List<StatusResultItem> getItems() {
            return items;
        }

        public void setTotalTickets(int totalTickets) {
            this.totalTickets = totalTickets;
        }

        public void setAlreadyScanned(int alreadyScanned) {
            this.alreadyScanned = alreadyScanned;
        }
    }

    CheckResult check(String ticketid, List<Answer> answers, boolean ignore_unpaid, boolean with_badge_data);

    CheckResult check(String ticketid);

    List<SearchResult> search(String query, int page) throws CheckException;

    StatusResult status() throws CheckException;

    public void setSentry(SentryInterface sentry);
}
