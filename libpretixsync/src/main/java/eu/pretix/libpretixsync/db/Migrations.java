package eu.pretix.libpretixsync.db;

import io.requery.meta.EntityModel;
import io.requery.sql.TableCreationMode;
import io.requery.sql.SchemaModifier;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class Migrations {
    private static EntityModel model = Models.DEFAULT;
    public static int CURRENT_VERSION = 38;

    private static void createVersionTable(Connection c, int version) throws SQLException {
        Statement s2 = c.createStatement();
        s2.execute("CREATE TABLE _version (version NUMERIC);");
        s2.close();
        Statement s3 = c.createStatement();
        s3.execute("INSERT INTO _version (version) VALUES (" + version + ");");
        s3.close();
    }

    private static void updateVersionTable(Connection c, int version) throws SQLException {
        Statement s2 = c.createStatement();
        s2.execute("DELETE FROM _version;");
        s2.close();
        Statement s3 = c.createStatement();
        s3.execute("INSERT INTO _version (version) VALUES (" + version + ");");
        s3.close();
    }

    public static void migrate(DataSource dataSource, boolean dbIsNew) throws SQLException {
        Connection c = dataSource.getConnection();
        int db_version = 0;

        if (dbIsNew) {
            new SchemaModifier(dataSource, model).createTables(TableCreationMode.CREATE_NOT_EXISTS);
            createVersionTable(c, CURRENT_VERSION);
            return;
        }
        Statement s = null;
        try {
            s = c.createStatement();
            ResultSet rs = s.executeQuery("SELECT version FROM _version LIMIT 1");
            while (rs.next()) {
                db_version = rs.getInt("version");
            }
        } catch (SQLException e) {
            db_version = 1;
            createVersionTable(c, db_version);
        } finally {
            if (s != null) s.close();
        }

        if (db_version < 24) {
            create_drop(dataSource);
        }
        if (db_version < 28) {
            create_notexists(dataSource);
        }
        if (db_version < 30) {
            Statement s1 = c.createStatement();
            s1.execute("ALTER TABLE Receipt ADD payment_data TEXT;");
            s1.close();
            create_notexists(dataSource);
        }
        if (db_version < 32) {
            create_notexists(dataSource);
        }
        if (db_version < 33) {
            Statement s1 = c.createStatement();
            s1.execute("ALTER TABLE ResourceLastModified ADD status TEXT;");
            s1.close();
            create_notexists(dataSource);
        }
        if (db_version < 34) {
            Statement s1 = c.createStatement();
            s1.execute("ALTER TABLE QueuedCheckin ADD event_slug TEXT;");
            s1.close();
        }
        if (db_version < 35) {
            Statement s1 = c.createStatement();
            s1.execute("ALTER TABLE QueuedOrder ADD locked NUMBER DEFAULT(0);");
            s1.close();
        }
        if (db_version < 36) {
            Statement s1 = c.createStatement();
            s1.execute("ALTER TABLE QueuedOrder ADD idempotency_key TEXT;");
            s1.close();
        }
        if (db_version < 37) {
            Statement s1 = c.createStatement();
            s1.execute("ALTER TABLE Item ADD picture_filename TEXT;");
            s1.close();
        }
        if (db_version < 38) {
            Statement s1 = c.createStatement();
            s1.execute("ALTER TABLE ReceiptLine ADD seat_guid TEXT;");
            s1.close();
            Statement s2 = c.createStatement();
            s2.execute("ALTER TABLE ReceiptLine ADD seat_name TEXT;");
            s2.close();
        }
        // Note that the Android app currently does not use these queries!

        if (db_version < CURRENT_VERSION) {
            Statement s4 = c.createStatement();
            s4.execute("DELETE FROM ResourceLastModified;");
            s4.close();
        }

        updateVersionTable(c, CURRENT_VERSION);
    }

    private static void create_drop(DataSource dataSource) {
        new SchemaModifier(dataSource, model).createTables(TableCreationMode.DROP_CREATE);
    }

    private static void create_notexists(DataSource dataSource) {
        new SchemaModifier(dataSource, model).createTables(TableCreationMode.CREATE_NOT_EXISTS);
    }
}
