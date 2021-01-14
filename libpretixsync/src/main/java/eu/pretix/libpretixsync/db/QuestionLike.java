package eu.pretix.libpretixsync.db;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import eu.pretix.libpretixsync.check.QuestionType;
import eu.pretix.libpretixsync.utils.Patterns;

public abstract class QuestionLike {
    public static class ValidationException extends Exception {
        public ValidationException(String msg) {
            super(msg);
        }
    }

    public abstract QuestionType getType();

    public abstract String getQuestion();

    public abstract List<QuestionOption> getOptions();

    public abstract boolean requiresAnswer();

    public String getDefault() {
        return null;
    }

    public String clean_answer(String answer, List<QuestionOption> opts) throws AbstractQuestion.ValidationException {
        QuestionType type = getType();
        if (requiresAnswer()) {
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
                return new BigDecimal(answer).toPlainString();
            } catch (NumberFormatException e) {
                throw new ValidationException("Invalid number supplied");
            }
        } else if (type == QuestionType.EMAIL) {
            if (!Patterns.EMAIL_ADDRESS.matcher(answer).matches()) {
                throw new ValidationException("Invalid email address supplied");
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
