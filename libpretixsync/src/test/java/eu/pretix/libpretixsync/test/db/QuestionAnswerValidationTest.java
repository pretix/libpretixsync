package eu.pretix.libpretixsync.test.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import eu.pretix.libpretixsync.check.QuestionType;
import eu.pretix.libpretixsync.db.AbstractQuestion;
import eu.pretix.libpretixsync.db.Question;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.xml.bind.ValidationException;
import java.util.Arrays;
import java.util.Collection;


@RunWith(Parameterized.class)
public class QuestionAnswerValidationTest {

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {QuestionType.S, "a", "a"},
                {QuestionType.T, "b", "b"},
                {QuestionType.S, "", ""},
                {QuestionType.N, "3", "3"},
                {QuestionType.N, "2.56", "2.56"},
                {QuestionType.N, "abc", null},
                {QuestionType.B, "True", "True"},
                {QuestionType.B, "true", "True"},
                {QuestionType.B, "False", "False"},
                {QuestionType.B, "false", "False"},
                {QuestionType.B, "0", "False"},
                {QuestionType.B, "", "False"},
                // TODO: Date, time, datetime
        });
    }

    private QuestionType questionType;
    private String input;
    private String expected;

    public QuestionAnswerValidationTest(QuestionType questionType, String input, String expected) {
        this.questionType = questionType;
        this.input = input;
        this.expected = expected;
    }

    @Test
    public void test() {
        Question q = new Question();
        q.setType(questionType);
        if (expected == null) {
            try {
                q.clean_answer(input);
                fail("Expected an ValidationException to be thrown");
            } catch (Question.ValidationException e) {
            }
        } else {
            try {
                assertEquals(q.clean_answer(input), expected);
            } catch (AbstractQuestion.ValidationException e) {
                fail(e.getMessage());
            }
        }
    }
}