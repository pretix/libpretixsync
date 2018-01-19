package eu.pretix.libpretixsync.db;

import eu.pretix.libpretixsync.check.QuestionType;
import io.requery.*;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity(cacheable = false)
public class AbstractQuestion {

    public class ValidationException extends Exception {
        public ValidationException(String msg) {
            super(msg);
        }
    }

    @Generated
    @Key
    public Long id;

    public Long server_id;

    public String question;

    public QuestionType type;

    public boolean required;

    public Long position;

    @OneToMany
    List<QuestionOption> options;

    @ManyToMany
    @JunctionTable
    List<Item> items;

    public String clean_answer(String answer) throws ValidationException {
        if (required) {
            if (type == QuestionType.B) {
                if (!answer.equals("True") && !answer.equals("true")) {
                    throw new ValidationException("Question is required");
                }
            } else if (answer == null || answer.trim().equals("")) {
                throw new ValidationException("Question is required");
            }
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
            for (QuestionOption o : options) {
                if (o.getServer_id().toString().equals(answer)) {
                    return answer;
                }
            }
            throw new ValidationException("Invalid choice supplied");
        } else if (type == QuestionType.M) {
            Set<String> validChoices = new HashSet<>();
            for (QuestionOption o : options) {
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
