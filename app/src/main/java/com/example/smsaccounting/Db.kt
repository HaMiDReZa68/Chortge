package com.example.smsaccounting

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** هر بار داده‌ای عوض شود شمارنده زیاد می‌شود تا صفحه دوباره بارگذاری شود */
object Store {
    val changes = MutableStateFlow(0)
    fun notifyChanged() {
        changes.update { it + 1 }
    }
}

class Db private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "sms_accounting.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE tx (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                hash TEXT NOT NULL UNIQUE,
                sender TEXT NOT NULL,
                type TEXT NOT NULL,
                amount INTEGER NOT NULL,
                balance INTEGER,
                merchant TEXT NOT NULL,
                mkey TEXT NOT NULL,
                category TEXT NOT NULL,
                ts INTEGER NOT NULL,
                body TEXT NOT NULL,
                deleted INTEGER NOT NULL DEFAULT 0
            )"""
        )
        db.execSQL("CREATE INDEX idx_tx_ts ON tx(ts)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    /** true اگر تراکنش جدید ثبت شد؛ پیامک تکراری نادیده گرفته می‌شود */
    fun insert(t: Tx): Boolean {
        val d = writableDatabase
        val exists = d.rawQuery(
            "SELECT 1 FROM tx WHERE sender = ? AND body = ? AND ts BETWEEN ? AND ? LIMIT 1",
            arrayOf(t.sender, t.body, (t.ts - 120_000).toString(), (t.ts + 120_000).toString()),
        ).use { it.moveToFirst() }
        if (exists) return false

        val cv = ContentValues().apply {
            put("hash", t.hash)
            put("sender", t.sender)
            put("type", t.type.name)
            put("amount", t.amount)
            if (t.balance != null) put("balance", t.balance) else putNull("balance")
            put("merchant", t.merchant)
            put("mkey", t.mkey)
            put("category", t.category)
            put("ts", t.ts)
            put("body", t.body)
        }
        return d.insertWithOnConflict("tx", null, cv, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    fun query(from: Long, to: Long): List<Tx> {
        val out = ArrayList<Tx>()
        readableDatabase.query(
            "tx", null,
            "ts >= ? AND ts < ? AND deleted = 0",
            arrayOf(from.toString(), to.toString()),
            null, null, "ts DESC",
        ).use { c ->
            while (c.moveToNext()) out.add(c.toTx())
        }
        return out
    }

    fun updateCategory(id: Long, category: String) {
        writableDatabase.execSQL("UPDATE tx SET category = ? WHERE id = ?", arrayOf<Any>(category, id))
    }

    fun updateCategoryByMkey(mkey: String, category: String) {
        writableDatabase.execSQL(
            "UPDATE tx SET category = ? WHERE mkey = ? AND type = 'EXPENSE' AND deleted = 0",
            arrayOf<Any>(category, mkey),
        )
    }

    fun updateType(id: Long, type: TxType, category: String) {
        writableDatabase.execSQL(
            "UPDATE tx SET type = ?, category = ? WHERE id = ?",
            arrayOf<Any>(type.name, category, id),
        )
    }

    fun softDelete(id: Long) {
        writableDatabase.execSQL("UPDATE tx SET deleted = 1 WHERE id = ?", arrayOf<Any>(id))
    }

    private fun Cursor.toTx(): Tx {
        val balIdx = getColumnIndexOrThrow("balance")
        return Tx(
            id = getLong(getColumnIndexOrThrow("id")),
            hash = getString(getColumnIndexOrThrow("hash")),
            sender = getString(getColumnIndexOrThrow("sender")),
            type = TxType.valueOf(getString(getColumnIndexOrThrow("type"))),
            amount = getLong(getColumnIndexOrThrow("amount")),
            balance = if (isNull(balIdx)) null else getLong(balIdx),
            merchant = getString(getColumnIndexOrThrow("merchant")),
            mkey = getString(getColumnIndexOrThrow("mkey")),
            category = getString(getColumnIndexOrThrow("category")),
            ts = getLong(getColumnIndexOrThrow("ts")),
            body = getString(getColumnIndexOrThrow("body")),
        )
    }

    companion object {
        @Volatile
        private var inst: Db? = null

        fun get(ctx: Context): Db =
            inst ?: synchronized(this) {
                inst ?: Db(ctx.applicationContext).also { inst = it }
            }
    }
}
