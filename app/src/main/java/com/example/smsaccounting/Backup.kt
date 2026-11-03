package com.example.smsaccounting

import android.content.Context
import org.json.JSONObject

object Backup {
    fun toBytes(ctx: Context): ByteArray {
        val root = Db.get(ctx).dump()
        root.put("prefs", Prefs(ctx).dump())
        root.put("app", "sms-accounting")
        root.put("created", System.currentTimeMillis())
        return root.toString().toByteArray(Charsets.UTF_8)
    }

    fun fileName(): String = "sms-accounting-backup-" + Jalali.ymd(System.currentTimeMillis()).replace('/', '-') + ".json"

    /** true اگر فایل معتبر بود و بازیابی شد */
    fun restore(ctx: Context, bytes: ByteArray): Boolean {
        val root = try {
            JSONObject(String(bytes, Charsets.UTF_8))
        } catch (e: Exception) {
            return false
        }
        if (root.optString("app") != "sms-accounting" || !root.has("tables")) return false
        try {
            Db.get(ctx).restore(root)
            root.optJSONObject("prefs")?.let { Prefs(ctx).load(it) }
        } catch (e: Exception) {
            return false
        }
        Look.setCustomIcons(Db.get(ctx).categories().associate { it.name to it.icon })
        Alarms.rescheduleAll(ctx)
        val p = Prefs(ctx)
        if (p.reminderOn) Reminder.schedule(ctx) else Reminder.cancel(ctx)
        Store.notifyChanged()
        return true
    }
}
