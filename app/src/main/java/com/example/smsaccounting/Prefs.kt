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

    var reminderOn: Boolean
        get() = sp.getBoolean("rem_on", false)
        set(v) {
            sp.edit().putBoolean("rem_on", v).apply()
        }

    var reminderH: Int
        get() = sp.getInt("rem_h", 21)
        set(v) {
            sp.edit().putInt("rem_h", v).apply()
        }

    var reminderM: Int
        get() = sp.getInt("rem_m", 0)
        set(v) {
            sp.edit().putInt("rem_m", v).apply()
        }

    /** فرستنده‌هایی که کاربر خوانده‌شدنشان را خاموش کرده */
    fun disabledSenders(): Set<String> =
        HashSet(sp.getStringSet("disabled_senders", emptySet()) ?: emptySet())

    fun setSenderEnabled(sender: String, enabled: Boolean) {
        val s = disabledSenders().toMutableSet()
        if (enabled) s.remove(sender) else s.add(sender)
        sp.edit().putStringSet("disabled_senders", s).apply()
    }

    fun bankName(sender: String): String? = sp.getString("bn_$sender", null)?.takeIf { it.isNotBlank() }

    fun setBankName(sender: String, name: String) {
        sp.edit().putString("bn_$sender", name).apply()
    }
}
