package eu.pretix.libpretixsync.sqldelight

import app.cash.sqldelight.db.AfterVersion

object Migrations {
    /**
     * Lowest schema version that we can still migrate from
     */
    const val MIN_SUPPORTED_VERSION = 77L

    /**
     * Database name
     *
     * Corresponds to requery's Models.DEFAULT.name
     */
    const val DEFAULT_DATABASE_NAME = "default"

    /**
     * AfterVersion callback that can be used in SQLDelight code migrations to crash if we encounter
     * a schema version that we have no migrations for.
     * Without it, SQLDelight might just increase the DB version without actually creating the
     * correct schema.
     */
    val minVersionCallback = AfterVersion(MIN_SUPPORTED_VERSION - 1L) { _ ->
        throw IllegalStateException("Unsupported database version. Minimum supported version is $MIN_SUPPORTED_VERSION")
    }
}
