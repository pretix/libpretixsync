package eu.pretix.libpretixsync.db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import eu.pretix.libpretixsync.Models;
import io.requery.meta.EntityModel;
import io.requery.sql.SchemaModifier;
import io.requery.sql.TableCreationMode;

public class Migrations {
    private static EntityModel model = Models.DEFAULT;
    public static int CURRENT_VERSION = 65;

    private static void createVersionTable(Connection c, int version) throws SQLException {
        Statement s2 = c.createStatement();
        execIgnore(c, "CREATE TABLE _version (version NUMERIC);", "duplicate column name");
        s2.close();
        Statement s3 = c.createStatement();
        execIgnore(c, "INSERT INTO _version (version) VALUES (" + version + ");", "duplicate column name");
        s3.close();
    }

    private static void updateVersionTable(Connection c, int version) throws SQLException {
        Statement s2 = c.createStatement();
        execIgnore(c, "DELETE FROM _version;", "duplicate column name");
        s2.close();
        Statement s3 = c.createStatement();
        execIgnore(c, "INSERT INTO _version (version) VALUES (" + version + ");", "duplicate column name");
        s3.close();
    }

    private static void execIgnore(Connection c, String sql, String ignoreMatch) throws SQLException {
        Statement s1 = c.createStatement();
        try {
            s1.execute(sql);
        } catch (SQLException e) {
            if (!e.getMessage().contains(ignoreMatch)) {
                throw e;
            }
        } finally {
            s1.close();
        }
    }

    private static void exec(Connection c, String sql) throws SQLException {
        Statement s1 = c.createStatement();
        s1.execute(sql);
        s1.close();
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
            updateVersionTable(c, 24);
        }
        if (db_version < 28) {
            create_notexists(dataSource);
            updateVersionTable(c, 28);
        }
        if (db_version < 30) {
            execIgnore(c, "ALTER TABLE Receipt ADD payment_data TEXT;", "duplicate column name");
            create_notexists(dataSource);
            updateVersionTable(c, 30);
        }
        if (db_version < 32) {
            create_notexists(dataSource);
            updateVersionTable(c, 32);
        }
        if (db_version < 33) {
            execIgnore(c, "ALTER TABLE ResourceLastModified ADD status TEXT;", "duplicate column name");
            create_notexists(dataSource);
            updateVersionTable(c, 33);
        }
        if (db_version < 34) {
            execIgnore(c, "ALTER TABLE QueuedCheckin ADD event_slug TEXT;", "duplicate column name");
            updateVersionTable(c, 34);
        }
        if (db_version < 35) {
            execIgnore(c, "ALTER TABLE QueuedOrder ADD locked NUMBER DEFAULT(0);", "duplicate column name");
            updateVersionTable(c, 35);
        }
        if (db_version < 36) {
            execIgnore(c, "ALTER TABLE QueuedOrder ADD idempotency_key TEXT;", "duplicate column name");
            updateVersionTable(c, 36);
        }
        if (db_version < 37) {
            execIgnore(c, "ALTER TABLE Item ADD picture_filename TEXT;", "duplicate column name");
            updateVersionTable(c, 37);
        }
        if (db_version < 38) {
            execIgnore(c, "ALTER TABLE ReceiptLine ADD seat_guid TEXT;", "duplicate column name");
            execIgnore(c, "ALTER TABLE ReceiptLine ADD seat_name TEXT;", "duplicate column name");
            updateVersionTable(c, 38);
        }
        if (db_version < 39) {
            execIgnore(c, "CREATE INDEX orderposition_secret ON orderposition (secret);", "already exists");
            updateVersionTable(c, 39);
        }
        if (db_version < 46) {
            Statement s4 = c.createStatement();
            execIgnore(c, "ALTER TABLE Receipt ADD email_to TEXT;", "duplicate column name");
            s4.close();
            updateVersionTable(c, 46);
        }
        if (db_version < 47) {
            execIgnore(c, "ALTER TABLE ReceiptLine ADD price_calculated_from_net INT;", "duplicate column name");
            execIgnore(c, "ALTER TABLE ReceiptLine ADD canceled_because_of_receipt INT;", "duplicate column name");
            execIgnore(c, "ALTER TABLE Receipt ADD fiscalisation_data TEXT;", "duplicate column name");
            execIgnore(c, "ALTER TABLE Receipt ADD fiscalisation_text TEXT;", "duplicate column name");
            execIgnore(c, "ALTER TABLE Receipt ADD fiscalisation_qr TEXT;", "duplicate column name");
            execIgnore(c, "ALTER TABLE Receipt ADD started DATE;", "duplicate column name");
            updateVersionTable(c, 47);
        }
        if (db_version < 48) {
            Statement s4 = c.createStatement();
            execIgnore(c, "ALTER TABLE Closing ADD invoice_settings TEXT;", "duplicate column name");
            s4.close();
            updateVersionTable(c, 48);
        }
        if (db_version < 49) {
            Statement s3 = c.createStatement();
            execIgnore(c, "ALTER TABLE Item ADD ticket_layout_pretixpos_id INT;", "duplicate column name");
            s3.close();
            updateVersionTable(c, 49);
        }
        if (db_version < 50) {
            Statement s1 = c.createStatement();
            execIgnore(c, "ALTER TABLE QueuedCheckin ADD datetime_string TEXT;", "duplicate column name");
            s1.close();
            updateVersionTable(c, 50);
        }
        if (db_version < 51) {
            Statement s1 = c.createStatement();
            execIgnore(c, "ALTER TABLE QueuedCheckin ADD type TEXT;", "duplicate column name");
            s1.close();
            s1 = c.createStatement();
            execIgnore(c, "ALTER TABLE CheckIn ADD type TEXT;", "duplicate column name");
            s1.close();
            updateVersionTable(c, 51);
        }
        if (db_version < 52) {
            Statement s2 = c.createStatement();
            execIgnore(c, "ALTER TABLE ReceiptLine ADD answers TEXT;", "duplicate column name");
            s2.close();
            updateVersionTable(c, 52);
        }
        if (db_version < 53) {
            exec(c, "CREATE TABLE IF NOT EXISTS Settings (id integer primary key, address TEXT, city varchar(255), country varchar(255), json_data TEXT, name varchar(255), slug varchar(255), tax_id varchar(255), vat_id varchar(255), zipcode varchar(255));");
            updateVersionTable(c, 53);
        }
        if (db_version < 54) {
            execIgnore(c, "ALTER TABLE ReceiptLine ADD attendee_name INT;", "duplicate column name");
            execIgnore(c, "ALTER TABLE ReceiptLine ADD attendee_email INT;", "duplicate column name");
            execIgnore(c, "ALTER TABLE ReceiptLine ADD attendee_company TEXT;", "duplicate column name");
            execIgnore(c, "ALTER TABLE ReceiptLine ADD attendee_street TEXT;", "duplicate column name");
            execIgnore(c, "ALTER TABLE ReceiptLine ADD attendee_zipcode TEXT;", "duplicate column name");
            execIgnore(c, "ALTER TABLE ReceiptLine ADD attendee_city DATE;", "duplicate column name");
            execIgnore(c, "ALTER TABLE ReceiptLine ADD attendee_country DATE;", "duplicate column name");
            updateVersionTable(c, 54);
        }
        if (db_version < 56) {
            execIgnore(c, "ALTER TABLE orders ADD deleteAfterTimestamp NUMERIC;", "duplicate column name");
            updateVersionTable(c, 56);
        }
        if (db_version < 57) {
            execIgnore(c, "CREATE INDEX orderposition_order ON orderposition (order_ref);", "already exists");
            execIgnore(c, "CREATE INDEX checkin_position ON CheckIn (position);", "already exists");
            updateVersionTable(c, 57);
        }
        if (db_version < 58) {
            execIgnore(c, "ALTER TABLE CheckIn ADD listId INT;", "duplicate column name");
            execIgnore(c, "UPDATE CheckIn SET listId = list WHERE (listID = 0 OR listId IS NULL);", "no such column: list");
            execIgnore(c, "CREATE INDEX checkin_listid ON CheckIn (listId);", "already exists");
            updateVersionTable(c, 58);
        }
        if (db_version < 59) {
            execIgnore(c, "ALTER TABLE ResourceLastModified ADD \"meta\" TEXT;", "duplicate column name");
            updateVersionTable(c, 59);
        }
        if (db_version < 60) {
            execIgnore(c, "ALTER TABLE CheckIn ADD server_id NUMERIC NULL;", "duplicate column name");
            execIgnore(c, "CREATE INDEX checkin_server_id ON CheckIn (server_id);", "already exists");
            updateVersionTable(c, 60);
        }
        if (db_version < 61) {
            create_notexists(dataSource);
            updateVersionTable(c, 61);
        }
        if (db_version < 62) {
            create_notexists(dataSource);
            updateVersionTable(c, 62);
        }
        if (db_version < 63) {
            execIgnore(c, "ALTER TABLE Receipt ADD chosen_cart_id TEXT NULL;", "duplicate column name");
            updateVersionTable(c, 63);
        }
        if (db_version < 64) {
            create_notexists(dataSource);
            updateVersionTable(c, 64);
        }
        if (db_version < 65) {
            create_notexists(dataSource);
            updateVersionTable(c, 64);
        }

        // Note that the Android app currently does not use these queries!

        if (db_version < CURRENT_VERSION) {
            exec(c, "DELETE FROM ResourceLastModified;");
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
