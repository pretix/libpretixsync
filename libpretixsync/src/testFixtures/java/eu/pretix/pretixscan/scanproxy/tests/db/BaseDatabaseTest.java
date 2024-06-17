package eu.pretix.pretixscan.scanproxy.tests.db;

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver;
import eu.pretix.libpretixsync.db.*;
import eu.pretix.libpretixsync.Models;
import eu.pretix.libpretixsync.sqldelight.BigDecimalAdapter;
import eu.pretix.libpretixsync.sqldelight.CheckIn;
import eu.pretix.libpretixsync.sqldelight.Closing;
import eu.pretix.libpretixsync.sqldelight.Event;
import eu.pretix.libpretixsync.sqldelight.JavaUtilDateAdapter;
import eu.pretix.libpretixsync.sqldelight.Receipt;
import eu.pretix.libpretixsync.sqldelight.ReceiptLine;
import eu.pretix.libpretixsync.sqldelight.ReceiptPayment;
import eu.pretix.libpretixsync.sqldelight.SubEvent;
import eu.pretix.libpretixsync.sqldelight.SyncDatabase;
import io.requery.Persistable;
import io.requery.cache.EntityCacheBuilder;
import io.requery.sql.Configuration;
import io.requery.sql.ConfigurationBuilder;
import io.requery.sql.EntityDataStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Formatter;
import java.util.Properties;
import java.util.Random;


public abstract class BaseDatabaseTest {

    @Rule
    public TestName name = new TestName();

    protected EntityDataStore<Persistable> dataStore;
    private Connection connection;

    protected SyncDatabase db;

    private static String byteArray2Hex(final byte[] hash) {
        Formatter formatter = new Formatter();
        for (byte b : hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }

    @Before
    public void setUpDataStore() throws SQLException, NoSuchAlgorithmException {
        byte[] randomBytes = new byte[32]; // length is bounded by 7
        new Random().nextBytes(randomBytes);
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        md.update(name.getMethodName().getBytes());
        md.update(randomBytes);
        String dbname = byteArray2Hex(md.digest());
        String sourceUrl = "jdbc:sqlite:file:" + dbname + "?mode=memory&cache=shared";

        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl(sourceUrl);
        SQLiteConfig config = new SQLiteConfig();
        config.setDateClass("TEXT");
        dataSource.setConfig(config);
        dataSource.setEnforceForeignKeys(true);
        Migrations.migrate(dataSource, true);
        connection = dataSource.getConnection();

        Configuration configuration = new ConfigurationBuilder(dataSource, Models.DEFAULT)
                .useDefaultLogging()
                .setEntityCache(new EntityCacheBuilder(Models.DEFAULT)
                        .useReferenceCache(false)
                        .useSerializableCache(false)
                        .build())
                .build();
        dataStore = new EntityDataStore<>(configuration);

        setUpDb(sourceUrl);
    }

    private void setUpDb(String sourceUrl) {
        JdbcSqliteDriver driver = new JdbcSqliteDriver(sourceUrl, new Properties());
        JavaUtilDateAdapter dateAdapter = new JavaUtilDateAdapter();
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
                new Receipt.Adapter(
                        dateAdapter,
                        dateAdapter
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
                        dateAdapter,
                        dateAdapter
                )
        );
    }

    @After
    public void tearDownDataStore() throws Exception {
        dataStore.close();
        connection.close();
    }
}
