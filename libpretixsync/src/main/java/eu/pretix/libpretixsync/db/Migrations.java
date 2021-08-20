package eu.pretix.libpretixsync.db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import eu.pretix.libpretixsync.BuildConfig;
import eu.pretix.libpretixsync.Models;
import io.requery.meta.EntityModel;
import io.requery.sql.SchemaModifier;
import io.requery.sql.TableCreationMode;

public class Migrations {
    private static EntityModel model = Models.DEFAULT;
    public static int CURRENT_VERSION = 79;

    private static void createVersionTable(Connection c, int version) throws SQLException {
        Statement s2 = c.createStatement();
        execIgnore(c, "CREATE TABLE _version (version NUMERIC);", new String[] {"duplicate column name", "already exists", "existiert bereits"});
        s2.close();
        Statement s3 = c.createStatement();
        execIgnore(c, "INSERT INTO _version (version) VALUES (" + version + ");", new String[] {"duplicate column name", "already exists", "existiert bereits"});
        s3.close();
    }

    private static void updateVersionTable(Connection c, int version) throws SQLException {
        Statement s2 = c.createStatement();
        execIgnore(c, "DELETE FROM _version;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
        s2.close();
        Statement s3 = c.createStatement();
        execIgnore(c, "INSERT INTO _version (version) VALUES (" + version + ");", new String[] {"duplicate column name", "already exists", "existiert bereits"});
        s3.close();
    }

    private static void execIgnore(Connection c, String sql, String[] ignoreMatch) throws SQLException {
        Statement s1 = c.createStatement();
        try {
            s1.execute(sql);
        } catch (SQLException e) {
            for (String m : ignoreMatch) {
                if (e.getMessage().contains(m)) {
                    return;
                }
            }
            throw e;
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
            execIgnore(c, "ALTER TABLE Receipt ADD payment_data TEXT;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            create_notexists(dataSource);
            updateVersionTable(c, 30);
        }
        if (db_version < 32) {
            create_notexists(dataSource);
            updateVersionTable(c, 32);
        }
        if (db_version < 33) {
            //execIgnore(c, "ALTER TABLE ResourceLastModified ADD status TEXT;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            create_notexists(dataSource);
            updateVersionTable(c, 33);
        }
        if (db_version < 34) {
            execIgnore(c, "ALTER TABLE QueuedCheckin ADD event_slug TEXT;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            updateVersionTable(c, 34);
        }
        if (db_version < 35) {
            execIgnore(c, "ALTER TABLE QueuedOrder ADD locked NUMBER DEFAULT(0);", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            updateVersionTable(c, 35);
        }
        if (db_version < 36) {
            execIgnore(c, "ALTER TABLE QueuedOrder ADD idempotency_key TEXT;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            updateVersionTable(c, 36);
        }
        if (db_version < 37) {
            execIgnore(c, "ALTER TABLE Item ADD picture_filename TEXT;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            updateVersionTable(c, 37);
        }
        if (db_version < 38) {
            execIgnore(c, "ALTER TABLE ReceiptLine ADD seat_guid TEXT;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            execIgnore(c, "ALTER TABLE ReceiptLine ADD seat_name TEXT;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            updateVersionTable(c, 38);
        }
        if (db_version < 39) {
            execIgnore(c, "CREATE INDEX orderposition_secret ON orderposition (secret);", new String[] {"already exists", "existiert bereits"});
            updateVersionTable(c, 39);
        }
        if (db_version < 46) {
            Statement s4 = c.createStatement();
            execIgnore(c, "ALTER TABLE Receipt ADD email_to TEXT;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            s4.close();
            updateVersionTable(c, 46);
        }
        if (db_version < 47) {
            execIgnore(c, "ALTER TABLE ReceiptLine ADD price_calculated_from_net INT;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            execIgnore(c, "ALTER TABLE ReceiptLine ADD canceled_because_of_receipt INT;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            execIgnore(c, "ALTER TABLE Receipt ADD fiscalisation_data TEXT;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            execIgnore(c, "ALTER TABLE Receipt ADD fiscalisation_text TEXT;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            execIgnore(c, "ALTER TABLE Receipt ADD fiscalisation_qr TEXT;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            execIgnore(c, "ALTER TABLE Receipt ADD started DATE;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            updateVersionTable(c, 47);
        }
        if (db_version < 48) {
            Statement s4 = c.createStatement();
            execIgnore(c, "ALTER TABLE Closing ADD invoice_settings TEXT;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            s4.close();
            updateVersionTable(c, 48);
        }
        if (db_version < 49) {
            Statement s3 = c.createStatement();
            execIgnore(c, "ALTER TABLE Item ADD ticket_layout_pretixpos_id INT;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            s3.close();
            updateVersionTable(c, 49);
        }
        if (db_version < 50) {
            Statement s1 = c.createStatement();
            execIgnore(c, "ALTER TABLE QueuedCheckin ADD datetime_string TEXT;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            s1.close();
            updateVersionTable(c, 50);
        }
        if (db_version < 51) {
            Statement s1 = c.createStatement();
            execIgnore(c, "ALTER TABLE QueuedCheckin ADD type TEXT;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            s1.close();
            s1 = c.createStatement();
            execIgnore(c, "ALTER TABLE CheckIn ADD type TEXT;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            s1.close();
            updateVersionTable(c, 51);
        }
        if (db_version < 52) {
            Statement s2 = c.createStatement();
            execIgnore(c, "ALTER TABLE ReceiptLine ADD answers TEXT;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            s2.close();
            updateVersionTable(c, 52);
        }
        if (db_version < 53) {
            exec(c, "CREATE TABLE IF NOT EXISTS Settings (id integer primary key, address TEXT, city varchar(255), country varchar(255), json_data TEXT, name varchar(255), slug varchar(255), tax_id varchar(255), vat_id varchar(255), zipcode varchar(255));");
            updateVersionTable(c, 53);
        }
        if (db_version < 54) {
            execIgnore(c, "ALTER TABLE ReceiptLine ADD attendee_name INT;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            execIgnore(c, "ALTER TABLE ReceiptLine ADD attendee_email INT;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            execIgnore(c, "ALTER TABLE ReceiptLine ADD attendee_company TEXT;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            execIgnore(c, "ALTER TABLE ReceiptLine ADD attendee_street TEXT;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            execIgnore(c, "ALTER TABLE ReceiptLine ADD attendee_zipcode TEXT;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            execIgnore(c, "ALTER TABLE ReceiptLine ADD attendee_city DATE;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            execIgnore(c, "ALTER TABLE ReceiptLine ADD attendee_country DATE;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            updateVersionTable(c, 54);
        }
        if (db_version < 56) {
            execIgnore(c, "ALTER TABLE orders ADD deleteAfterTimestamp NUMERIC;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            updateVersionTable(c, 56);
        }
        if (db_version < 57) {
            execIgnore(c, "CREATE INDEX orderposition_order ON orderposition (order_ref);", new String[] {"already exists", "existiert bereits"});
            execIgnore(c, "CREATE INDEX checkin_position ON CheckIn (position);", new String[] {"already exists", "existiert bereits"});
            updateVersionTable(c, 57);
        }
        if (db_version < 58) {
            execIgnore(c, "ALTER TABLE CheckIn ADD listId INT;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            execIgnore(c, "UPDATE CheckIn SET listId = list WHERE (listID = 0 OR listId IS NULL);", new String[] {"no such column: list"});
            execIgnore(c, "CREATE INDEX checkin_listid ON CheckIn (listId);", new String[] {"already exists", "existiert bereits"});
            updateVersionTable(c, 58);
        }
        if (db_version < 59) {
            //execIgnore(c, "ALTER TABLE ResourceLastModified ADD \"meta\" TEXT;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            updateVersionTable(c, 59);
        }
        if (db_version < 60) {
            execIgnore(c, "ALTER TABLE CheckIn ADD server_id NUMERIC NULL;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            execIgnore(c, "CREATE INDEX checkin_server_id ON CheckIn (server_id);", new String[] {"already exists", "existiert bereits"});
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
            execIgnore(c, "ALTER TABLE Receipt ADD chosen_cart_id TEXT NULL;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
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
        if (db_version < 66) {
            execIgnore(c, "ALTER TABLE Cashier ADD active " + BuildConfig.BOOLEAN_TYPE + " DEFAULT(" + BuildConfig.BOOLEAN_FALSE + ");", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            updateVersionTable(c, 66);
        }
        if (db_version < 67) {
            execIgnore(c, "ALTER TABLE Receipt ADD cashier_numericid NUMERIC NULL;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            execIgnore(c, "ALTER TABLE Receipt ADD cashier_userid TEXT NULL;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            execIgnore(c, "ALTER TABLE Receipt ADD cashier_name TEXT NULL;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            execIgnore(c, "ALTER TABLE Closing ADD cashier_numericid NUMERIC NULL;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            execIgnore(c, "ALTER TABLE Closing ADD cashier_userid TEXT NULL;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            execIgnore(c, "ALTER TABLE Closing ADD cashier_name TEXT NULL;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            updateVersionTable(c, 67);
        }
        if (db_version < 68) {
            execIgnore(c, "ALTER TABLE Receipt ADD training " + BuildConfig.BOOLEAN_TYPE + " DEFAULT(" + BuildConfig.BOOLEAN_FALSE + ");", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            updateVersionTable(c, 68);
        }
        if (db_version < 69) {
            create_notexists(dataSource);
            updateVersionTable(c, 69);
        }
        if (db_version < 70) {
            create_notexists(dataSource);
            updateVersionTable(c, 70);
        }
        if (db_version < 71) {
            execIgnore(c, "ALTER TABLE Quota ADD available NUMERIC NULL;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            execIgnore(c, "ALTER TABLE Quota ADD available_number NUMERIC NULL;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            updateVersionTable(c, 71);
        }
        if (db_version < 71) {
            execIgnore(c, "ALTER TABLE Quota ADD size NUMERIC NULL;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            updateVersionTable(c, 72);
        }
        if (db_version < 73) {
            if (BuildConfig.DB == "postgres") {
                exec(c, "alter table queuedcall alter column body type text using body::text;");
                exec(c, "alter table queuedcall alter column url type text using body::text;");
            }
            updateVersionTable(c, 73);
        }
        if (db_version < 77) {
            execIgnore(c, "ALTER TABLE settings ADD covid_certificates_allow_vaccinated " + BuildConfig.BOOLEAN_TYPE + " NULL;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            execIgnore(c, "ALTER TABLE settings ADD covid_certificates_allow_vaccinated_min NUMERIC NULL;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            execIgnore(c, "ALTER TABLE settings ADD covid_certificates_allow_vaccinated_max NUMERIC NULL;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            execIgnore(c, "ALTER TABLE settings ADD covid_certificates_record_proof_vaccinated " + BuildConfig.BOOLEAN_TYPE + " NULL;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            execIgnore(c, "ALTER TABLE settings ADD covid_certificates_allow_cured " + BuildConfig.BOOLEAN_TYPE + " NULL;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            execIgnore(c, "ALTER TABLE settings ADD covid_certificates_allow_cured_min NUMERIC NULL;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            execIgnore(c, "ALTER TABLE settings ADD covid_certificates_allow_cured_max NUMERIC NULL;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            execIgnore(c, "ALTER TABLE settings ADD covid_certificates_record_proof_cured " + BuildConfig.BOOLEAN_TYPE + " NULL;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            execIgnore(c, "ALTER TABLE settings ADD covid_certificates_allow_tested_pcr " + BuildConfig.BOOLEAN_TYPE + " NULL;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            execIgnore(c, "ALTER TABLE settings ADD covid_certificates_allow_tested_pcr_min NUMERIC NULL;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            execIgnore(c, "ALTER TABLE settings ADD covid_certificates_allow_tested_pcr_max NUMERIC NULL;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            execIgnore(c, "ALTER TABLE settings ADD covid_certificates_record_proof_tested_pcr " + BuildConfig.BOOLEAN_TYPE + " NULL;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            execIgnore(c, "ALTER TABLE settings ADD covid_certificates_allow_tested_antigen_unknown " + BuildConfig.BOOLEAN_TYPE + " NULL;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            execIgnore(c, "ALTER TABLE settings ADD covid_certificates_allow_tested_antigen_unknown_min NUMERIC NULL;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            execIgnore(c, "ALTER TABLE settings ADD covid_certificates_allow_tested_antigen_unknown_max NUMERIC NULL;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            execIgnore(c, "ALTER TABLE settings ADD covid_certificates_record_proof_tested_antigen_unknown " + BuildConfig.BOOLEAN_TYPE + " NULL;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            execIgnore(c, "ALTER TABLE settings ADD covid_certificates_accept_eudgc " + BuildConfig.BOOLEAN_TYPE + " NULL;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            execIgnore(c, "ALTER TABLE settings ADD covid_certificates_accept_manual " + BuildConfig.BOOLEAN_TYPE + " NULL;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            updateVersionTable(c, 77);
        }
        if (db_version < 78) {
            execIgnore(c, "ALTER TABLE Receipt ADD additional_text TEXT NULL;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            execIgnore(c, "ALTER TABLE settings ADD pretixpos_additional_receipt_text TEXT NULL;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            updateVersionTable(c, 78);
        }
        if (db_version < 79) {
            execIgnore(c, "ALTER TABLE settings ADD covid_certificates_allow_other " + BuildConfig.BOOLEAN_TYPE + " NULL;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            execIgnore(c, "ALTER TABLE settings ADD covid_certificates_record_proof_other " + BuildConfig.BOOLEAN_TYPE + " NULL;", new String[] {"duplicate column name", "already exists", "existiert bereits"});
            updateVersionTable(c, 79);
        }

        // Note that the Android app currently does not use these queries!

        if (db_version < CURRENT_VERSION) {
            exec(c, "DELETE FROM ResourceSyncStatus;");
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
