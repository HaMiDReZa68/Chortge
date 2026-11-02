package com.example.smsaccounting

import android.content.Context
import android.provider.Telephony

object Importer {

    private fun build(sender: String, body: String, ts: Long, learned: Map<String, String>): Tx? {
        val p = SmsParser.parse(body) ?: return null
        return Tx(
            hash = sha1("$sender|$ts|$body"),
            sender = sender,
            type = p.type,
            amount = p.amount,
            balance = p.balance,
            merchant = p.merchant,
            mkey = Categorizer.mkey(p.merchant),
            category = Categorizer.categorize(p.type, body, p.merchant, learned),
            ts = ts,
            body = body,
        )
    }

    /** پیامک تازه‌رسیده (از SmsReceiver) */
    fun handle(ctx: Context, sender: String, body: String, ts: Long): Boolean {
        val tx = build(sender, body, ts, Prefs(ctx).learned()) ?: return false
        val ok = Db.get(ctx).insert(tx)
        if (ok) Store.notifyChanged()
        return ok
    }

    /** خواندن صندوق ورودی؛ تعداد تراکنش جدید را برمی‌گرداند */
    fun importInbox(ctx: Context, all: Boolean): Int {
        val prefs = Prefs(ctx)
        val db = Db.get(ctx)
        val learned = prefs.learned()
        val since = if (all) 0L else prefs.lastImport
        var maxTs = since
        var added = 0

        ctx.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} > ?",
            arrayOf(since.toString()),
            "${Telephony.Sms.DATE} ASC",
        )?.use { c ->
            val ia = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val ib = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val id = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (c.moveToNext()) {
                val ts = c.getLong(id)
                if (ts > maxTs) maxTs = ts
                val body = c.getString(ib) ?: continue
                val tx = build(c.getString(ia) ?: "", body, ts, learned) ?: continue
                if (db.insert(tx)) added++
            }
        }
        prefs.lastImport = maxTs
        return added
    }
}
