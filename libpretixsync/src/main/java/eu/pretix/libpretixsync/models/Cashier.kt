package eu.pretix.libpretixsync.models

import eu.pretix.libpretixsync.db.CashierLike
import org.json.JSONException
import org.json.JSONObject

class Cashier(
    val id: Long,
    val active: Boolean,
    private val pin: String,
    private val nfcUid: String,
    name: String,
    numericId: Long? = null,
    userId: String? = null,
    team: JSONObject? = null,
) : CashierLike {
    private val _name = name
    private val _numericId = numericId
    private val _userId = userId
    private val _team = team

    override fun checkPIN(pin: String): Boolean {
        return if (!active) {
            false
        } else {
            this.pin == pin
        }
    }

    override fun validOnDevice(device: String): Boolean {
        if (!active) {
            return false
        }

        try {
            val team: JSONObject = _team ?: return false
            if (team.optBoolean("all_devices", false)) {
                return true
            }
            val devices = team.getJSONArray("devices")
            for (i in 0 until devices.length()) {
                val d = devices.getString(i)
                if (d == device) {
                    return true
                }
            }
            return false
        } catch (e: JSONException) {
            return false
        }
    }

    override fun hasPermission(permission: String): Boolean {
        val defaults: MutableMap<String, Boolean> = HashMap()
        defaults["can_open_drawer"] = true
        defaults["can_top_up_gift_cards"] = true
        defaults["can_check_in_tickets"] = true
        if (!active) {
            return false
        }

        try {
            val team: JSONObject = _team ?: return false
            return team.optBoolean(permission, defaults.getOrDefault(permission, false))
        } catch (e: JSONException) {
            return false
        }
    }

    override fun hasNfcUid(): Boolean {
        return this.nfcUid.isNotBlank()
    }

    override fun getNumericId(): Long? {
        return _numericId
    }

    override fun getUserId(): String? {
        return _userId
    }

    override fun getName(): String {
        return _name
    }
}
