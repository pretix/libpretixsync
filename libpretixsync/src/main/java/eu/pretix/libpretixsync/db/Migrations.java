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
    public static int CURRENT_VERSION = 56;

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
        if (db_version < 39) {
            Statement s4 = c.createStatement();
            s4.execute("CREATE INDEX orderposition_secret ON orderposition (secret);");
            s4.close();
        }
        if (db_version < 46) {
            Statement s4 = c.createStatement();
            s4.execute("ALTER TABLE Receipt ADD email_to TEXT;");
            s4.close();
        }
        if (db_version < 47) {
            Statement s2 = c.createStatement();
            s2.execute("ALTER TABLE ReceiptLine ADD price_calculated_from_net INT;");
            s2.close();
            Statement s3 = c.createStatement();
            s3.execute("ALTER TABLE ReceiptLine ADD canceled_because_of_receipt INT;");
            s3.close();
            Statement s4 = c.createStatement();
            s4.execute("ALTER TABLE Receipt ADD fiscalisation_data TEXT;");
            s4.close();
            Statement s5 = c.createStatement();
            s5.execute("ALTER TABLE Receipt ADD fiscalisation_text TEXT;");
            s5.close();
            Statement s6 = c.createStatement();
            s6.execute("ALTER TABLE Receipt ADD fiscalisation_qr TEXT;");
            s6.close();
            Statement s7 = c.createStatement();
            s7.execute("ALTER TABLE Receipt ADD started DATE;");
            s7.close();
        }
        if (db_version < 48) {
            Statement s4 = c.createStatement();
            s4.execute("ALTER TABLE Closing ADD invoice_settings TEXT;");
            s4.close();
        }
        if (db_version < 49) {
            Statement s3 = c.createStatement();
            s3.execute("ALTER TABLE Item ADD ticket_layout_pretixpos_id INT;");
            s3.close();
        }
        if (db_version < 50) {
            Statement s1 = c.createStatement();
            s1.execute("ALTER TABLE QueuedCheckin ADD datetime_string TEXT;");
            s1.close();
        }
        if (db_version < 51) {
            Statement s1 = c.createStatement();
            s1.execute("ALTER TABLE QueuedCheckin ADD type TEXT;");
            s1.close();
            s1 = c.createStatement();
            s1.execute("ALTER TABLE CheckIn ADD type TEXT;");
            s1.close();
        }
        if (db_version < 52) {
            Statement s2 = c.createStatement();
            s2.execute("ALTER TABLE ReceiptLine ADD answers TEXT;");
            s2.close();
        }
        if (db_version < 53) {
            Statement s2 = c.createStatement();
            s2.execute("CREATE TABLE Settings (id integer primary key autoincrement, address TEXT, city varchar(255), country varchar(255), json_data TEXT, name varchar(255), slug varchar(255), tax_id varchar(255), vat_id varchar(255), zipcode varchar(255));");
            s2.close();
        }
        if (db_version < 54) {
            Statement s2 = c.createStatement();
            s2.execute("ALTER TABLE ReceiptLine ADD attendee_name INT;");
            s2.close();
            Statement s3 = c.createStatement();
            s3.execute("ALTER TABLE ReceiptLine ADD attendee_email INT;");
            s3.close();
            Statement s4 = c.createStatement();
            s4.execute("ALTER TABLE ReceiptLine ADD attendee_company TEXT;");
            s4.close();
            Statement s5 = c.createStatement();
            s5.execute("ALTER TABLE ReceiptLine ADD attendee_street TEXT;");
            s5.close();
            Statement s6 = c.createStatement();
            s6.execute("ALTER TABLE ReceiptLine ADD attendee_zipcode TEXT;");
            s6.close();
            Statement s7 = c.createStatement();
            s7.execute("ALTER TABLE ReceiptLine ADD attendee_city DATE;");
            s7.close();
            Statement s8 = c.createStatement();
            s8.execute("ALTER TABLE ReceiptLine ADD attendee_country DATE;");
            s8.close();
        }
        if (db_version < 56) {
            Statement s2 = c.createStatement();
            s2.execute("ALTER TABLE Order ADD deleteAfterTimestamp NUMERIC;");
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
