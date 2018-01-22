package eu.pretix.libpretixsync.db;

import eu.pretix.libpretixsync.check.QuestionType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;


public class QuestionAnswerRequiredValidationTest {

    @Test
    public void testBoolean() throws AbstractQuestion.ValidationException {
        Question q = new Question();
        q.setType(QuestionType.B);
        q.setRequired(true);
        q.clean_answer("True", q.getOptions());
        try {
            q.clean_answer("False", q.getOptions());
            fail("Expected an ValidationException to be thrown");
        } catch (Question.ValidationException e) {
        }
    }

    @Test
    public void testText() throws AbstractQuestion.ValidationException {
        Question q = new Question();
        q.setType(QuestionType.T);
        q.setRequired(true);
        q.clean_answer("True", q.getOptions());
        try {
            q.clean_answer("", q.getOptions());
            fail("Expected an ValidationException to be thrown");
        } catch (Question.ValidationException e) {
        }
    }
}