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
}
