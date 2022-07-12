package eu.pretix.libpretixsync.test

import eu.pretix.libpretixsync.config.ConfigStore
import eu.pretix.libpretixsync.api.PretixApi
import org.json.JSONObject

class FakeConfigStore : ConfigStore {
    private var last_download: Long = 0
    private var last_sync: Long = 0
    private var last_cleanup: Long = 0
    private var last_failed_sync: Long = 0
    private var last_failed_sync_msg: String? = null
    private val last_status_data: String? = null
    private var allow_search = false
    fun setAllow_search(allow_search: Boolean) {
        this.allow_search = allow_search
    }

    override fun isDebug(): Boolean {
        return false
    }

    override fun isConfigured(): Boolean {
        return true
    }

    override fun getApiVersion(): Int {
        return PretixApi.SUPPORTED_API_VERSION
    }

    override fun getApiUrl(): String {
        return "http://example.org"
    }

    override fun getDeviceKnownVersion(): Int {
        return 0
    }

    override fun setDeviceKnownVersion(value: Int) {}
    override fun getDeviceKnownInfo(): JSONObject {
        return JSONObject()
    }

    override fun setDeviceKnownInfo(value: JSONObject) {}
    override fun getDeviceKnownName(): String {
        return last_failed_sync_msg!!
    }

    override fun setDeviceKnownName(`val`: String) {
        last_failed_sync_msg = `val`
    }

    override fun getDeviceKnownGateName(): String? {
        return null
    }

    override fun setDeviceKnownGateName(value: String) {}
    override fun getApiKey(): String {
        return "12345"
    }

    override fun getOrganizerSlug(): String {
        return "demo"
    }

    val eventSlug: String
        get() = "demo"
    val subEventId: Long?
        get() = null

    override fun getLastDownload(): Long {
        return last_download
    }

    override fun setLastDownload(`val`: Long) {
        last_download = `val`
    }

    override fun getLastSync(): Long {
        return last_sync
    }

    override fun setLastSync(`val`: Long) {
        last_sync = `val`
    }

    override fun getLastCleanup(): Long {
        return last_cleanup
    }

    override fun setLastCleanup(`val`: Long) {
        last_cleanup = `val`
    }

    override fun getLastFailedSync(): Long {
        return last_failed_sync
    }

    override fun setLastFailedSync(`val`: Long) {
        last_failed_sync = `val`
    }

    override fun getLastFailedSyncMsg(): String {
        return last_failed_sync_msg!!
    }

    override fun setLastFailedSyncMsg(`val`: String) {
        last_failed_sync_msg = `val`
    }

    override fun getPosId(): Long? {
        return null
    }

    override fun setKnownPretixVersion(`val`: Long) {}
    override fun getKnownPretixVersion(): Long {
        return 0L
    }

    override fun getAutoSwitchRequested(): Boolean {
        return false
    }

    override fun getSyncCycleId(): String {
        return "1"
    }

    override fun getSynchronizedEvents(): List<String> {
        return listOf("demo")
    }

    override fun getSelectedSubeventForEvent(event: String): Long? {
        return null
    }

    override fun getSelectedCheckinListForEvent(event: String): Long {
        return 1L
    }
}