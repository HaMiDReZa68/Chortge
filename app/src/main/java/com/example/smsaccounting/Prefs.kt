package com.example.smsaccounting

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    var toman: Boolean
        get() = sp.getBoolean("toman", true)
        set(v) {
            sp.edit().putBoolean("toman", v).apply()
        }

    /** تاریخ جدیدترین پیامکی که قبلاً بررسی شده */
    var lastImport: Long
        get() = sp.getLong("last_import", 0L)
        set(v) {
            sp.edit().putLong("last_import", v).apply()
        }

    /** فروشنده → دسته‌ای که کاربر دستی انتخاب کرده */
    fun learned(): Map<String, String> =
        sp.all
            .filterKeys { it.startsWith("m_") }
            .mapKeys { it.key.removePrefix("m_") }
            .mapValues { it.value.toString() }

    fun learn(mkey: String, category: String) {
        sp.edit().putString("m_$mkey", category).apply()
    }
}
