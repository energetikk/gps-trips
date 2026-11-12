package ru.gpstrips.data

import android.content.Context

class Prefs(ctx: Context) {

    private val sp = ctx.getSharedPreferences("gps_trips", Context.MODE_PRIVATE)

    /** Путь к папке с логами: либо "/storage/...", либо "content://...". */
    var target: String?
        get() = sp.getString("target", null)
        set(v) {
            sp.edit().putString("target", v).apply()
        }

    var tgToken: String
        get() = sp.getString("tg_token", "") ?: ""
        set(v) {
            sp.edit().putString("tg_token", v.trim()).apply()
        }

    var tgChat: String
        get() = sp.getString("tg_chat", "") ?: ""
        set(v) {
            sp.edit().putString("tg_chat", v.trim()).apply()
        }

    /** Полный адрес приёма координат на сайте, например https://example.ru/api/position. */
    var liveUrl: String
        get() = sp.getString("live_url", "") ?: ""
        set(v) {
            sp.edit().putString("live_url", v.trim()).apply()
        }

    /** Токен устройства: сервер сверяет его с TRACK_TOKEN. */
    var liveToken: String
        get() = sp.getString("live_token", "") ?: ""
        set(v) {
            sp.edit().putString("live_token", v.trim()).apply()
        }

    var liveDevice: String
        get() = sp.getString("live_device", "car")?.ifBlank { "car" } ?: "car"
        set(v) {
            sp.edit().putString("live_device", v.trim().ifBlank { "car" }).apply()
        }

    /** Как часто отправлять координаты, секунды. */
    var liveIntervalSec: Int
        get() = sp.getInt("live_interval", 3).coerceIn(MIN_INTERVAL_SEC, MAX_INTERVAL_SEC)
        set(v) {
            sp.edit().putInt("live_interval", v.coerceIn(MIN_INTERVAL_SEC, MAX_INTERVAL_SEC)).apply()
        }

    var liveEnabled: Boolean
        get() = sp.getBoolean("live_enabled", false)
        set(v) {
            sp.edit().putBoolean("live_enabled", v).apply()
        }

    /** Выгружать поездки в историю на сайте. */
    var historyEnabled: Boolean
        get() = sp.getBoolean("history_enabled", true)
        set(v) {
            sp.edit().putBoolean("history_enabled", v).apply()
        }

    /**
     * Подписи уже выгруженных поездок. Хранятся в виде "id\u001Fконец:точек":
     * если трек доехал, подпись меняется и поездка отправится заново.
     */
    fun syncedTrips(): Set<String> =
        sp.getStringSet(KEY_SYNCED, emptySet()) ?: emptySet()

    fun markTripsSynced(entries: Map<String, String>) {
        if (entries.isEmpty()) return
        val current = sp.getStringSet(KEY_SYNCED, emptySet())?.toMutableSet() ?: mutableSetOf()
        for ((id, signature) in entries) {
            val prefix = "$id\u001F"
            current.removeAll { it.startsWith(prefix) }
            current.add(prefix + signature)
        }
        sp.edit().putStringSet(KEY_SYNCED, current).apply()
    }

    fun clearSyncedTrips() {
        sp.edit().remove(KEY_SYNCED).apply()
    }

    companion object {
        const val MIN_INTERVAL_SEC = 2
        const val MAX_INTERVAL_SEC = 60
        private const val KEY_SYNCED = "synced_trips"
    }
}
