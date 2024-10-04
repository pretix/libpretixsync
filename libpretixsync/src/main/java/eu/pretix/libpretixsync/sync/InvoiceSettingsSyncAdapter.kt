package eu.pretix.libpretixsync.sync

import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.sqldelight.SyncDatabase
import eu.pretix.libpretixsync.sync.SyncManager.ProgressFeedback

class InvoiceSettingsSyncAdapter(
    db: SyncDatabase,
    eventSlug: String,
    key: String,
    api: PretixApi,
    syncCycleId: String,
    feedback: ProgressFeedback? = null,
) : SettingsSyncAdapter(
    db = db,
    eventSlug = eventSlug,
    key = key,
    api = api,
    syncCycleId = syncCycleId,
    feedback = feedback,
) {
    override fun getUrl(): String {
        return api.eventResourceUrl(eventSlug, "invoicesettings")
    }
}
