package eu.pretix.libpretixsync.models

import eu.pretix.libpretixsync.db.ItemAddOn
import eu.pretix.libpretixsync.db.ItemBundle
import eu.pretix.libpretixsync.db.ItemVariation
import eu.pretix.libpretixsync.db.MediaPolicy
import eu.pretix.libpretixsync.db.ReusableMediaType
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.math.BigDecimal
import java.util.Collections


class Item(
    val id: Long,
    val serverId: Long,
    val active: Boolean,
    val admission: Boolean,
    val name: String = "",
    val nameI18n: JSONObject = JSONObject(),
    val description: String = "",
    val descriptionI18n: JSONObject = JSONObject(),
    val internalName: String = "",
    val isPersonalized: Boolean = true,
    val hasVariations: Boolean = false,
    val hasDynamicValidityWithCustomStart: Boolean = false,
    val hasDynamicValidityWithTimeOfDay: Boolean = false,
    val dynamicValidityDayLimit: Long? = null,
    val categoryServerId: Long? = null,
    val checkInText: String? = null,
    val eventSlug: String? = null,
    val pictureFilename: String? = null,
    val position: Long? = null,
    val ticketLayoutServerId: Long? = null,
    val ticketLayoutPretixPosId: Long? = null,
    val requireVoucher: Boolean = true,
    val hideWithoutVoucher: Boolean = true,
    val isGiftcard: Boolean = false,
    val requireBundling: Boolean = false,
    val taxRuleId: Long = 0,
    val defaultPrice: BigDecimal = BigDecimal("0.00"),
    val hasFreePrice: Boolean = false,
    val mediaPolicy: MediaPolicy = MediaPolicy.NONE,
    val mediaType: ReusableMediaType = ReusableMediaType.NONE,
    val generateTickets: Boolean = false,
    val requiresCheckInAttention: Boolean = false,

    variations: JSONArray = JSONArray(),
    bundles: JSONArray = JSONArray(),
    addons: JSONArray = JSONArray(),
    salesChannels: JSONArray? = null,
) {
    private val _variations = variations
    private val _bundles = bundles
    private val _addons = addons
    private val _salesChannels = salesChannels

    val variations: List<ItemVariation>
        get() {
            val l: MutableList<ItemVariation> = ArrayList()
            val vars: JSONArray = _variations
            for (i in 0 until vars.length()) {
                val variation = vars.getJSONObject(i)
                val v = ItemVariation()
                v.isActive = variation.getBoolean("active")
                v.description = variation.optJSONObject("description")
                v.position = variation.getLong("position")
                v.price = BigDecimal(variation.getString("price"))
                v.server_id = variation.getLong("id")
                v.value = variation.getJSONObject("value")
                v.available_from = variation.optString("available_from")
                v.available_until = variation.optString("available_until")
                v.sales_channels = variation.optJSONArray("sales_channels")
                v.isHide_without_voucher = variation.optBoolean("hide_without_voucher", false)
                v.isCheckin_attention = variation.optBoolean("checkin_attention", false)
                v.checkin_text = variation.optString("checkin_text")
                l.add(v)
            }
            return l
        }

    fun getVariation(variationServerId: Long): ItemVariation? =
        variations.firstOrNull { it.server_id ==  variationServerId }

    val bundles: List<ItemBundle>
        get() {
            val l: MutableList<ItemBundle> = ArrayList()
            val objects: JSONArray = _bundles
            for (i in 0 until objects.length()) {
                val obj = objects.getJSONObject(i)
                val v = ItemBundle()
                v.bundledItemId = obj.getLong("bundled_item")
                v.bundledVariationId =
                    if (obj.isNull("bundled_variation")) null else obj.getLong("bundled_variation")
                v.count = obj.getInt("count")
                v.designatedPrice =
                    if (obj.isNull("designated_price")) null else BigDecimal(obj.getString("designated_price"))
                l.add(v)
            }
            return l
        }

    val addons: List<ItemAddOn>
        get() {
            val l: MutableList<ItemAddOn> = java.util.ArrayList()
            val objects: JSONArray = _addons
            for (i in 0 until objects.length()) {
                val obj = objects.getJSONObject(i)
                val v = ItemAddOn()
                v.addonCategoryId = obj.getLong("addon_category")
                v.minCount = obj.getInt("min_count")
                v.maxCount = obj.getInt("max_count")
                v.position = obj.getInt("position")
                v.isMultiAllowed = obj.getBoolean("multi_allowed")
                v.isPriceIncluded = obj.getBoolean("price_included")
                l.add(v)
            }
            Collections.sort(l, Comparator.comparingInt { obj: ItemAddOn -> obj.position })
            return l
        }

    val salesChannels: List<String>?
        get() {
            return try {
                val l = mutableListOf<String>()
                val channels: JSONArray = _salesChannels ?: return null
                for (i in 0 until channels.length()) {
                    l.add(channels.getString(i))
                }
                l
            } catch (e: JSONException) {
                e.printStackTrace()
                null
            }
        }
}
