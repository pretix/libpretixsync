package eu.pretix.libpretixsync.db;

import eu.pretix.libpretixsync.check.QuestionType;
import io.requery.*;

import java.math.BigDecimal;
import java.util.List;

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
        if (type == QuestionType.N) {
            try {
                return new BigDecimal(answer.toString()).toPlainString();
            } catch (NumberFormatException e) {
                throw new ValidationException("Invalid number supplied");
            }
        } else if (type == QuestionType.B) {
            return (answer.equals("True") || answer.equals("true")) ? "True" : "False";
        }
        return answer.toString();
    }
}
