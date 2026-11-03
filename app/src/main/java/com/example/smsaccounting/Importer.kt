package com.example.smsaccounting

import android.content.Context
import android.provider.Telephony

object Importer {

    private fun suggest(ctx: Context, db: Db, type: TxType, body: String, merchant: String, learned: Map<String, String>) =
        Categorizer.categorize(type, body, merchant, learned, db.catCtx(type))

    /** پیامک‌های بانکی صندوق ورودی از تاریخ مشخص، فقط برای سرشماره‌های همین حساب */
    fun importForAccount(ctx: Context, accountId: Long, sinceMs: Long): Int {
        val db = Db.get(ctx)
        val prefs = Prefs(ctx)
        val learned = prefs.learned()
        val sources = db.sources().filter { it.accountId == accountId }
        if (sources.isEmpty()) return 0
        val keys = sources.map { Norm.sender(it.sender) }.toSet()
        var added = 0
        var lastBal: Pair<Long, Long>? = null
        val expCtx = db.catCtx(TxType.EXPENSE)
        val incCtx = db.catCtx(TxType.INCOME)

        ctx.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(sinceMs.toString()),
            "${Telephony.Sms.DATE} ASC",
        )?.use { c ->
            val ia = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val ib = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val id = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (c.moveToNext()) {
                val sender = c.getString(ia) ?: continue
                if (Norm.sender(sender) !in keys) continue
                val body = c.getString(ib) ?: continue
                val ts = c.getLong(id)
                val src = db.findSource(sender, body) ?: continue
                if (src.accountId != accountId) continue
                val p = SmsParser.parse(body) ?: continue
                val r = Categorizer.categorize(p.type, body, p.merchant, learned, if (p.type == TxType.EXPENSE) expCtx else incCtx)
                val tx = Tx(
                    hash = sha1("$sender|$ts|$body"),
                    sender = sender,
                    type = p.type,
                    amount = p.amount,
                    balance = p.balance,
                    merchant = p.merchant,
                    mkey = Categorizer.mkey(p.merchant),
                    category = r.category,
                    ts = ts,
                    body = body,
                    accountId = accountId,
                    sub = r.sub,
                )
                if (db.insert(tx)) added++
                if (p.balance != null) lastBal = p.balance to ts
            }
        }
        lastBal?.let { db.setBalance(accountId, it.first, it.second) }
        return added
    }

    /** سرشماره‌هایی که حداقل یک پیامک تراکنشی دارند؛ برای انتخاب هنگام افزودن حساب */
    fun scanSenders(ctx: Context): List<SenderCandidate> {
        val map = LinkedHashMap<String, SenderCandidate>()
        val since = System.currentTimeMillis() - 180L * 86_400_000L
        ctx.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(since.toString()),
            "${Telephony.Sms.DATE} DESC",
        )?.use { c ->
            val ia = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val ib = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val id = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (c.moveToNext()) {
                val sender = c.getString(ia) ?: continue
                val body = c.getString(ib) ?: continue
                if (SmsParser.parse(body) == null) continue
                val key = Norm.sender(sender)
                val old = map[key]
                map[key] = if (old == null) {
                    SenderCandidate(sender, 1, c.getLong(id), body)
                } else {
                    old.copy(count = old.count + 1)
                }
            }
        }
        return map.values.sortedByDescending { it.count }
    }

    /** پیامک تازه‌رسیده: فقط اگر سرشمارهٔ ثبت‌شده باشد، در انتظار ثبت می‌ماند و اعلان می‌آید */
    fun handleIncoming(ctx: Context, sender: String, body: String, ts: Long): Boolean {
        val db = Db.get(ctx)
        val src = db.findSource(sender, body) ?: return false
        val p = SmsParser.parse(body) ?: return false
        if (p.balance != null) db.setBalance(src.accountId, p.balance, ts)
        val r = suggest(ctx, db, p.type, body, p.merchant, Prefs(ctx).learned())
        val id = db.insertPending(
            PendingTx(
                id = 0,
                hash = sha1("$sender|$ts|$body"),
                accountId = src.accountId,
                sender = sender,
                type = p.type,
                amount = p.amount,
                balance = p.balance,
                merchant = p.merchant,
                category = r.category,
                sub = r.sub,
                ts = ts,
                body = body,
            )
        )
        if (id < 0) return false
        Store.notifyChanged()
        val acc = db.account(src.accountId)
        Notifs.showNewTx(ctx, id, acc?.name ?: "", p.type, p.amount, Prefs(ctx).toman)
        return true
    }
}
