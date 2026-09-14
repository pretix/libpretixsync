package eu.pretix.libpretixsync.sqldelight

import app.cash.sqldelight.Transacter
import app.cash.sqldelight.TransactionWithReturn
import app.cash.sqldelight.TransactionWithoutReturn
import eu.pretix.libpretixsync.SentryInterface

/**
 * Workaround to start explicit read-write transaction
 *
 * SQLDelight starts all transactions in DEFERRED mode. As a result, transactions that start with a
 * read-only query but perform a write further down the line start off as a read-only transaction
 * and are then upgraded to read-write on the fly.
 *
 * If someone else holds a write lock at the moment of this upgrade, the transaction will instantly
 * fail with an SQLITE_BUSY error - unlike explicit read-write transactions which would wait for a
 * busy timeout first (if configured).
 * The easiest way to avoid this would be to start the transaction with BEGIN IMMEDIATE, but the
 * SQLDelight APIs do not provide this option. As a workaround, issue a no-op write statement before
 * the original transaction contents. This will force an upgrade to a read-write transaction right
 * away.
 */
fun Transacter.writeTransaction(
    db: SyncDatabase,
    sentry: SentryInterface? = null,
    noEnclosing: Boolean = true,
    body: TransactionWithoutReturn.() -> Unit,
) {
    try {
        transaction(noEnclosing) {
            db.compatQueries.upgradeTransaction()
            body()
        }
    } catch (e: IllegalStateException) {
        sentry?.captureException(e)
        e.printStackTrace()

        transaction(false) {
            db.compatQueries.upgradeTransaction()
            body()
        }
    }
}

fun <R> Transacter.writeTransactionWithResult(
    db: SyncDatabase,
    sentry: SentryInterface? = null,
    noEnclosing: Boolean = true,
    bodyWithReturn: TransactionWithReturn<R>.() -> R,
): R {
    val ret = try {
        transactionWithResult(noEnclosing) {
            db.compatQueries.upgradeTransaction()
            bodyWithReturn()
        }
    } catch (e: IllegalStateException) {
        sentry?.captureException(e)
        e.printStackTrace()

        transactionWithResult(false) {
            db.compatQueries.upgradeTransaction()
            bodyWithReturn()
        }
    }

    return ret
}
