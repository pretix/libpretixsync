package eu.pretix.libpretixsync.db;

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
                {QuestionType.C, "3", "3"},
                {QuestionType.C, "4", null},
                {QuestionType.C, "A", null},
                {QuestionType.M, "3", "3"},
                {QuestionType.M, "12", "12"},
                {QuestionType.M, "3,12", "3,12"},
                {QuestionType.M, "3,12,6", null},
                {QuestionType.M, "6", null},
                {QuestionType.D, "2018-01-19", "2018-01-19"},
                {QuestionType.D, "2016-02-29", "2016-02-29"},
                {QuestionType.D, "2017-02-29", null},
                {QuestionType.D, "fooobar", null},
                {QuestionType.H, "12:20", "12:20"},
                {QuestionType.H, "25:30", null},
                {QuestionType.H, "Foo", null},
                {QuestionType.W, "2018-01-19T12:20", "2018-01-19T12:20"},
                {QuestionType.W, "2016-02-29T14:30", "2016-02-29T14:30"},
                {QuestionType.W, "2016-02-29T25:59", null},
                {QuestionType.W, "2017-02-01", null},
                {QuestionType.W, "fooobar", null},
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

        QuestionOption qo = new QuestionOption();
        qo.setServer_id(3L);
        qo.setValue("A");
        q.getOptions().add(qo);
        qo = new QuestionOption();
        qo.setServer_id(12L);
        qo.setValue("B");
        q.getOptions().add(qo);

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