package eu.pretix.libpretixsync.utils

import eu.pretix.libpretixsync.models.Settings

interface SettingsManager {
    fun getBySlug(eventSlug: String): Settings?
}