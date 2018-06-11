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
    public static int CURRENT_VERSION = 10;

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

        if (db_version < 5) {
            migrate_from_4_to_5(dataSource);
        }
        if (db_version < 5) {
            migrate_from_4_to_5(dataSource);
        }
        // Note that the Android app currently does not use these queries!

        updateVersionTable(c, CURRENT_VERSION);
    }

    private static void migrate_from_4_to_5(DataSource dataSource) {
        new SchemaModifier(dataSource, model).createTables(TableCreationMode.DROP_CREATE);
    }

    private static void migrate_from_4_to_5(DataSource dataSource) throws SQLException {
        Connection c = dataSource.getConnection();
        Statement s = c.createStatement();
        s.execute("ALTER TABLE Ticket ADD COLUMN addon_text TEXT;");
        s.close();
    }
}
