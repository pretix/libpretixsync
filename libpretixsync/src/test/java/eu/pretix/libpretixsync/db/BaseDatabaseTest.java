package eu.pretix.libpretixsync.db;

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver;
import eu.pretix.libpretixsync.sqldelight.BigDecimalAdapter;
import eu.pretix.libpretixsync.sqldelight.CheckIn;
import eu.pretix.libpretixsync.sqldelight.Closing;
import eu.pretix.libpretixsync.sqldelight.Event;
import eu.pretix.libpretixsync.sqldelight.JavaOffsetDateTimeAdapter;
import eu.pretix.libpretixsync.sqldelight.JavaUtilDateAdapter;
import eu.pretix.libpretixsync.sqldelight.QueuedCheckIn;
import eu.pretix.libpretixsync.sqldelight.Receipt;
import eu.pretix.libpretixsync.sqldelight.ReceiptLine;
import eu.pretix.libpretixsync.sqldelight.ReceiptPayment;
import eu.pretix.libpretixsync.sqldelight.SubEvent;
import eu.pretix.libpretixsync.sqldelight.SyncDatabase;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
import java.util.Properties;
import java.util.Random;


public abstract class BaseDatabaseTest {

    @Rule
    public TestName name = new TestName();

    protected SyncDatabase db;

    private JdbcSqliteDriver driver;

    private static String byteArray2Hex(final byte[] hash) {
        Formatter formatter = new Formatter();
        for (byte b : hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }

    @Before
    public void setUpDb() throws NoSuchAlgorithmException {
        byte[] randomBytes = new byte[32]; // length is bounded by 7
        new Random().nextBytes(randomBytes);
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        md.update(name.getMethodName().getBytes());
        md.update(randomBytes);
        String dbname = byteArray2Hex(md.digest());
        String sourceUrl = "jdbc:sqlite:file:" + dbname + "?mode=memory&cache=shared";

        driver = new JdbcSqliteDriver(sourceUrl, new Properties());
        SyncDatabase.Companion.getSchema().create(driver);

        JavaUtilDateAdapter dateAdapter = new JavaUtilDateAdapter();
        JavaOffsetDateTimeAdapter offsetDateTimeAdapter = new JavaOffsetDateTimeAdapter();
        BigDecimalAdapter bigDecimalAdapter = new BigDecimalAdapter();

        db = SyncDatabase.Companion.invoke(
                driver,
                new CheckIn.Adapter(
                        dateAdapter
                ),
                new Closing.Adapter(
                        bigDecimalAdapter,
                        dateAdapter,
                        bigDecimalAdapter,
                        bigDecimalAdapter
                ),
                new Event.Adapter(
                        dateAdapter,
                        dateAdapter
                ),
                new QueuedCheckIn.Adapter(
                        dateAdapter
                ),
                new Receipt.Adapter(
                        offsetDateTimeAdapter,
                        offsetDateTimeAdapter
                ),
                new ReceiptLine.Adapter(
                        dateAdapter,
                        dateAdapter,
                        bigDecimalAdapter,
                        bigDecimalAdapter,
                        bigDecimalAdapter,
                        bigDecimalAdapter,
                        bigDecimalAdapter,
                        bigDecimalAdapter
                ),
                new ReceiptPayment.Adapter(
                        bigDecimalAdapter
                ),
                new SubEvent.Adapter(
                        offsetDateTimeAdapter,
                        offsetDateTimeAdapter
                )
        );
    }

    @After
    public void tearDownDb() {
        driver.close();
    }
}
