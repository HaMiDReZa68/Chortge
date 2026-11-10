package com.example.smsaccounting

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    var toman: Boolean
        get() = sp.getBoolean("toman", true)
        set(v) {
            sp.edit().putBoolean("toman", v).apply()
        }

    // ---- تنظیمات فاکتور
    fun invSettings() = InvSettings(
        name = sp.getString("inv_name", "") ?: "",
        address = sp.getString("inv_addr", "") ?: "",
        phone = sp.getString("inv_phone", "") ?: "",
        next = sp.getLong("inv_next", 1L),
        nextPro = sp.getLong("inv_next_pro", 1L),
        notes = sp.getString("inv_notes", "") ?: "",
        toman = sp.getBoolean("inv_toman", true),
        paper = sp.getString("inv_paper", "A4") ?: "A4",
        footer = sp.getBoolean("inv_footer", true),
    )

    fun saveInvSettings(s: InvSettings) {
        sp.edit().putString("inv_name", s.name).putString("inv_addr", s.address).putString("inv_phone", s.phone)
            .putLong("inv_next", s.next).putLong("inv_next_pro", s.nextPro).putString("inv_notes", s.notes).putBoolean("inv_toman", s.toman).putString("inv_paper", s.paper)
            .putBoolean("inv_footer", s.footer).apply()
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

    /** ۰ روشن، ۱ تیره، ۲ طبق گوشی */
    var themeMode: Int
        get() = sp.getInt("theme", 0)
        set(v) {
            sp.edit().putInt("theme", v).apply()
        }

    var lockType: String
        get() = sp.getString("lock_type", "none") ?: "none"
        set(v) {
            sp.edit().putString("lock_type", v).apply()
        }

    var lockLen: Int
        get() = sp.getInt("lock_len", 4)
        set(v) {
            sp.edit().putInt("lock_len", v).apply()
        }

    var lockBio: Boolean
        get() = sp.getBoolean("lock_bio", false)
        set(v) {
            sp.edit().putBoolean("lock_bio", v).apply()
        }

    /** چند ثانیه بعد از خروج از برنامه قفل شود */
    var lockDelay: Int
        get() = sp.getInt("lock_delay", 60)
        set(v) {
            sp.edit().putInt("lock_delay", v).apply()
        }

    private fun lockHash(salt: String, secret: String) = sha1(salt + "|" + secret)

    fun setLock(type: String, secret: String) {
        val salt = java.util.UUID.randomUUID().toString()
        sp.edit()
            .putString("lock_type", type)
            .putString("lock_salt", salt)
            .putString("lock_hash", lockHash(salt, secret))
            .putInt("lock_len", secret.length)
            .apply()
    }

    fun checkLock(secret: String): Boolean {
        val salt = sp.getString("lock_salt", null) ?: return false
        return sp.getString("lock_hash", null) == lockHash(salt, secret)
    }

    fun clearLock() {
        sp.edit().remove("lock_type").remove("lock_salt").remove("lock_hash").remove("lock_bio").apply()
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

    /** سقف بودجهٔ ماهانهٔ هر دسته (ریال) */
    fun budgets(): Map<String, Long> =
        sp.all
            .filterKeys { it.startsWith("bg_") }
            .mapKeys { it.key.removePrefix("bg_") }
            .mapNotNull { (k, v) -> (v as? Long)?.let { k to it } }
            .toMap()

    fun setBudget(category: String, rial: Long) {
        val e = sp.edit()
        if (rial > 0) e.putLong("bg_$category", rial) else e.remove("bg_$category")
        e.apply()
    }

    /** همهٔ تنظیمات به‌جز تاریخ آخرین بررسی، برای پشتیبان */
    fun dump(): org.json.JSONObject {
        val o = org.json.JSONObject()
        for ((k, v) in sp.all) {
            if (k == "last_import" || k.startsWith("lock_")) continue
            val e = org.json.JSONObject()
            when (v) {
                is Boolean -> e.put("t", "b").put("v", v)
                is Int -> e.put("t", "i").put("v", v)
                is Long -> e.put("t", "l").put("v", v)
                is String -> e.put("t", "s").put("v", v)
                else -> continue
            }
            o.put(k, e)
        }
        return o
    }

    fun load(o: org.json.JSONObject) {
        val ed = sp.edit()
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val e = o.optJSONObject(k) ?: continue
            when (e.optString("t")) {
                "b" -> ed.putBoolean(k, e.getBoolean("v"))
                "i" -> ed.putInt(k, e.getInt("v"))
                "l" -> ed.putLong(k, e.getLong("v"))
                "s" -> ed.putString(k, e.getString("v"))
            }
        }
        ed.apply()
    }
}
