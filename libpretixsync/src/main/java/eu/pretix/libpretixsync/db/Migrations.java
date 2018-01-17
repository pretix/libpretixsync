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
    public static int CURRENT_VERSION = 3;

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

        if (db_version < 2) {
            migrate_from_1_to_2(dataSource);
        }
        if (db_version < 3) {
            migrate_from_2_to_3(dataSource);
        }

        updateVersionTable(c, CURRENT_VERSION);
    }

    private static void migrate_from_1_to_2(DataSource dataSource) {
        new SchemaModifier(dataSource, model).createTables(TableCreationMode.CREATE_NOT_EXISTS);
    }

    private static void migrate_from_2_to_3(DataSource dataSource) throws SQLException {
        Connection c = dataSource.getConnection();
        Statement s = c.createStatement();
        s.execute("ALTER TABLE QueuedCheckIn ADD COLUMN answers TEXT;");
        s.close();
    }
}
