package com.example.smsaccounting

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

/** هر بار داده‌ای عوض شود شمارنده زیاد می‌شود تا صفحه دوباره بارگذاری شود */
object Store {
    val changes = MutableStateFlow(0)
    fun notifyChanged() {
        changes.update { it + 1 }
    }
}

private fun Cursor.str(n: String): String = getString(getColumnIndexOrThrow(n))
private fun Cursor.lng(n: String): Long = getLong(getColumnIndexOrThrow(n))
private fun Cursor.int(n: String): Int = getInt(getColumnIndexOrThrow(n))
private fun Cursor.lngOrNull(n: String): Long? {
    val i = getColumnIndexOrThrow(n)
    return if (isNull(i)) null else getLong(i)
}

class Db private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "sms_accounting.db", null, 3) {

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
                deleted INTEGER NOT NULL DEFAULT 0,
                account_id INTEGER NOT NULL DEFAULT 0,
                sub TEXT NOT NULL DEFAULT '',
                note TEXT NOT NULL DEFAULT ''
            )"""
        )
        db.execSQL("CREATE INDEX idx_tx_ts ON tx(ts)")
        createV2Tables(db)
        seed(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE tx ADD COLUMN account_id INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE tx ADD COLUMN sub TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE tx ADD COLUMN note TEXT NOT NULL DEFAULT ''")
            createV2Tables(db)
            seed(db)
            migrateSenders(db)
        }
        if (oldVersion == 2) db.execSQL("ALTER TABLE account ADD COLUMN opening INTEGER NOT NULL DEFAULT 0")
        if (oldVersion < 3) {
            // مانده از این به بعد = مانده اولیه + تراکنش‌های ثبت‌شده
            db.execSQL(
                "UPDATE account SET opening = COALESCE(last_balance, 0) - " + netSql("account.id")
            )
        }
    }

    private fun netSql(idExpr: String) =
        "COALESCE((SELECT SUM(CASE WHEN type IN ('INCOME','TRANSFER_IN') THEN amount ELSE -amount END) " +
            "FROM tx WHERE account_id = $idExpr AND deleted = 0), 0)"

    private fun createV2Tables(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE account (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                bank TEXT NOT NULL DEFAULT '',
                kind TEXT NOT NULL DEFAULT 'BANK',
                last_balance INTEGER,
                opening INTEGER NOT NULL DEFAULT 0,
                last_balance_ts INTEGER NOT NULL DEFAULT 0,
                deleted INTEGER NOT NULL DEFAULT 0
            )"""
        )
        db.execSQL(
            """CREATE TABLE sms_source (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sender TEXT NOT NULL,
                account_id INTEGER NOT NULL,
                matchtxt TEXT NOT NULL DEFAULT ''
            )"""
        )
        db.execSQL(
            """CREATE TABLE category (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                parent_id INTEGER NOT NULL DEFAULT 0,
                icon TEXT NOT NULL DEFAULT '',
                keywords TEXT NOT NULL DEFAULT '',
                sort INTEGER NOT NULL DEFAULT 0
            )"""
        )
        db.execSQL(
            """CREATE TABLE pending (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                hash TEXT NOT NULL UNIQUE,
                account_id INTEGER NOT NULL,
                sender TEXT NOT NULL,
                type TEXT NOT NULL,
                amount INTEGER NOT NULL,
                balance INTEGER,
                merchant TEXT NOT NULL,
                category TEXT NOT NULL,
                sub TEXT NOT NULL DEFAULT '',
                ts INTEGER NOT NULL,
                body TEXT NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE obligation (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                kind TEXT NOT NULL,
                title TEXT NOT NULL,
                party TEXT NOT NULL DEFAULT '',
                amount INTEGER NOT NULL DEFAULT 0,
                due_ts INTEGER NOT NULL,
                note TEXT NOT NULL DEFAULT '',
                done INTEGER NOT NULL DEFAULT 0,
                rep INTEGER NOT NULL DEFAULT 0,
                remaining INTEGER NOT NULL DEFAULT 0,
                created INTEGER NOT NULL
            )"""
        )
    }

    private fun seed(db: SQLiteDatabase) {
        db.execSQL("INSERT OR IGNORE INTO account(id, name, bank, kind) VALUES (1, 'نقدی', '', 'CASH')")
        var i = 0
        for (n in Categorizer.builtinExpense) insertCat(db, n, "EXPENSE", 0, Look.catIcon[n] ?: "🧾", "", i++)
        for (n in Categorizer.builtinIncome) insertCat(db, n, "INCOME", 0, Look.catIcon[n] ?: "💰", "", i++)
    }

    private fun insertCat(
        db: SQLiteDatabase,
        name: String,
        type: String,
        parent: Long,
        icon: String,
        keywords: String,
        sort: Int,
    ): Long {
        val cv = ContentValues().apply {
            put("name", name)
            put("type", type)
            put("parent_id", parent)
            put("icon", icon)
            put("keywords", keywords)
            put("sort", sort)
        }
        return db.insert("category", null, cv)
    }

    /** نسخهٔ قبلی اپ حساب نداشت؛ برای هر فرستنده یک حساب می‌سازیم تا داده‌ها از دست نروند */
    private fun migrateSenders(db: SQLiteDatabase) {
        val senders = ArrayList<String>()
        db.rawQuery("SELECT DISTINCT sender FROM tx WHERE sender != 'cash'", null).use { c ->
            while (c.moveToNext()) senders.add(c.getString(0))
        }
        for (s in senders) {
            val id = db.insert(
                "account", null,
                ContentValues().apply {
                    put("name", "حساب")
                    put("bank", BankNames.guess(s))
                    put("kind", "BANK")
                }
            )
            db.insert(
                "sms_source", null,
                ContentValues().apply {
                    put("sender", s)
                    put("account_id", id)
                    put("matchtxt", "")
                }
            )
            db.execSQL("UPDATE tx SET account_id = ? WHERE sender = ?", arrayOf<Any>(id, s))
            db.rawQuery(
                "SELECT balance, ts FROM tx WHERE sender = ? AND balance IS NOT NULL AND deleted = 0 " +
                    "ORDER BY ts DESC LIMIT 1",
                arrayOf(s),
            ).use { c ->
                if (c.moveToFirst()) {
                    db.execSQL(
                        "UPDATE account SET last_balance = ?, last_balance_ts = ? WHERE id = ?",
                        arrayOf<Any>(c.getLong(0), c.getLong(1), id),
                    )
                }
            }
        }
        db.execSQL("UPDATE tx SET account_id = 1 WHERE sender = 'cash'")
    }

    // ---------------------------------------------------------------- تراکنش‌ها

    /** true اگر تراکنش جدید ثبت شد؛ پیامک تکراری نادیده گرفته می‌شود */
    fun insert(t: Tx): Boolean {
        val d = writableDatabase
        val exists = t.sender != "cash" && t.sender != "manual" && d.rawQuery(
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
            put("account_id", t.accountId)
            put("sub", t.sub)
            put("note", t.note)
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

    fun search(q: String): List<Tx> {
        val like = "%" + q.trim() + "%"
        val out = ArrayList<Tx>()
        readableDatabase.query(
            "tx", null,
            "deleted = 0 AND (merchant LIKE ? OR body LIKE ? OR category LIKE ? OR sub LIKE ? OR note LIKE ?)",
            arrayOf(like, like, like, like, like),
            null, null, "ts DESC", "300",
        ).use { c ->
            while (c.moveToNext()) out.add(c.toTx())
        }
        return out
    }

    fun updateTx(id: Long, type: TxType, category: String, sub: String, note: String) {
        writableDatabase.execSQL(
            "UPDATE tx SET type = ?, category = ?, sub = ?, note = ? WHERE id = ?",
            arrayOf<Any>(type.name, category, sub, note, id),
        )
    }

    fun updateCategoryByMkey(mkey: String, category: String, sub: String) {
        writableDatabase.execSQL(
            "UPDATE tx SET category = ?, sub = ? WHERE mkey = ? AND type = 'EXPENSE' AND deleted = 0",
            arrayOf<Any>(category, sub, mkey),
        )
    }

    /** حذف تراکنش (و طرف دیگر انتقال دستی)؛ مانده‌ی ثبت‌های دستی برگردانده می‌شود */
    fun removeTx(tx: Tx) {
        val d = writableDatabase
        val rows = ArrayList<Tx>()
        if (tx.mkey.startsWith("tr:")) {
            d.query("tx", null, "mkey = ? AND deleted = 0", arrayOf(tx.mkey), null, null, null).use { c ->
                while (c.moveToNext()) rows.add(c.toTx())
            }
        } else {
            rows.add(tx)
        }
        for (r in rows) {
            d.execSQL("UPDATE tx SET deleted = 1 WHERE id = ?", arrayOf<Any>(r.id))
        }
    }

    fun softDelete(id: Long) {
        writableDatabase.execSQL("UPDATE tx SET deleted = 1 WHERE id = ?", arrayOf<Any>(id))
    }

    /** مجموع خالص تراکنش‌های نقدی (دخل منهای خرج) */
    fun cashNet(): Long {
        readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(CASE WHEN type IN ('INCOME','TRANSFER_IN') THEN amount ELSE -amount END), 0) " +
                "FROM tx WHERE account_id = 1 AND deleted = 0",
            null,
        ).use { c -> return if (c.moveToFirst()) c.getLong(0) else 0L }
    }

    fun txCount(accountId: Long): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM tx WHERE account_id = ? AND deleted = 0",
            arrayOf(accountId.toString()),
        ).use { c -> return if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    private fun Cursor.toTx(): Tx = Tx(
        id = lng("id"),
        hash = str("hash"),
        sender = str("sender"),
        type = TxType.valueOf(str("type")),
        amount = lng("amount"),
        balance = lngOrNull("balance"),
        merchant = str("merchant"),
        mkey = str("mkey"),
        category = str("category"),
        ts = lng("ts"),
        body = str("body"),
        accountId = lng("account_id"),
        sub = str("sub"),
        note = str("note"),
    )

    // ---------------------------------------------------------------- حساب‌ها و سرشماره‌ها

    /** lastBalance = مانده اولیه + مجموع تراکنش‌های ثبت‌شده (مانده‌ی پیامک ذخیره نمی‌شود) */
    fun accounts(): List<Account> {
        val out = ArrayList<Account>()
        readableDatabase.rawQuery(
            "SELECT *, opening + " + netSql("account.id") + " AS bal FROM account WHERE deleted = 0 ORDER BY id ASC",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    Account(
                        id = c.lng("id"),
                        name = c.str("name"),
                        bank = c.str("bank"),
                        kind = c.str("kind"),
                        lastBalance = c.lng("bal"),
                        lastBalanceTs = 0L,
                    )
                )
            }
        }
        return out
    }

    fun account(id: Long): Account? = accounts().firstOrNull { it.id == id }

    fun addAccount(name: String, bank: String): Long =
        writableDatabase.insert(
            "account", null,
            ContentValues().apply {
                put("name", name)
                put("bank", bank)
                put("kind", "BANK")
            }
        )

    fun updateAccount(id: Long, name: String, bank: String) {
        writableDatabase.execSQL(
            "UPDATE account SET name = ?, bank = ? WHERE id = ?",
            arrayOf<Any>(name, bank, id),
        )
    }

    /** مانده‌ی اولیه (قبل از اولین تراکنش ثبت‌شده) */
    fun setOpening(id: Long, opening: Long) {
        writableDatabase.execSQL("UPDATE account SET opening = ? WHERE id = ?", arrayOf<Any>(opening, id))
    }

    /** مانده‌ی فعلی را طوری تنظیم می‌کند که با تراکنش‌های ثبت‌شده جمع بشود */
    fun setCurrentBalance(id: Long, balance: Long) {
        writableDatabase.execSQL(
            "UPDATE account SET opening = ? - " + netSql("account.id") + " WHERE id = ?",
            arrayOf<Any>(balance, id),
        )
    }

    fun txExists(sender: String, body: String, ts: Long): Boolean =
        readableDatabase.rawQuery(
            "SELECT 1 FROM tx WHERE sender = ? AND body = ? AND ts BETWEEN ? AND ? AND deleted = 0 LIMIT 1",
            arrayOf(sender, body, (ts - 120_000).toString(), (ts + 120_000).toString()),
        ).use { it.moveToFirst() }

    fun deleteAccount(id: Long) {
        val d = writableDatabase
        d.beginTransaction()
        try {
            d.execSQL("UPDATE account SET deleted = 1 WHERE id = ?", arrayOf<Any>(id))
            d.execSQL("DELETE FROM sms_source WHERE account_id = ?", arrayOf<Any>(id))
            d.execSQL("UPDATE tx SET deleted = 1 WHERE account_id = ?", arrayOf<Any>(id))
            d.execSQL("DELETE FROM pending WHERE account_id = ?", arrayOf<Any>(id))
            d.setTransactionSuccessful()
        } finally {
            d.endTransaction()
        }
    }

    fun sources(): List<SmsSource> {
        val out = ArrayList<SmsSource>()
        readableDatabase.query("sms_source", null, null, null, null, null, "id ASC").use { c ->
            while (c.moveToNext()) {
                out.add(SmsSource(c.lng("id"), c.str("sender"), c.lng("account_id"), c.str("matchtxt")))
            }
        }
        return out
    }

    fun addSource(sender: String, accountId: Long, match: String): Long =
        writableDatabase.insert(
            "sms_source", null,
            ContentValues().apply {
                put("sender", sender)
                put("account_id", accountId)
                put("matchtxt", match)
            }
        )

    fun removeSource(id: Long) {
        writableDatabase.execSQL("DELETE FROM sms_source WHERE id = ?", arrayOf<Any>(id))
    }

    /** سرشمارهٔ پیامک را به یک حساب وصل می‌کند؛ اگر چند حساب یک سرشماره دارند، عبارت شناسایی تعیین‌کننده است */
    fun findSource(sender: String, body: String): SmsSource? {
        val key = Norm.sender(sender)
        val cands = sources().filter { Norm.sender(it.sender) == key }
        if (cands.isEmpty()) return null
        val text = Norm.normalize(body)
        return cands.firstOrNull { it.match.isNotBlank() && text.contains(Norm.normalize(it.match)) }
            ?: cands.firstOrNull { it.match.isBlank() }
    }

    // ---------------------------------------------------------------- در انتظار ثبت

    /** شناسهٔ ردیف جدید، یا -1 اگر قبلاً وجود داشته */
    fun insertPending(p: PendingTx): Long {
        val d = writableDatabase
        val cv = ContentValues().apply {
            put("hash", p.hash)
            put("account_id", p.accountId)
            put("sender", p.sender)
            put("type", p.type.name)
            put("amount", p.amount)
            if (p.balance != null) put("balance", p.balance) else putNull("balance")
            put("merchant", p.merchant)
            put("category", p.category)
            put("sub", p.sub)
            put("ts", p.ts)
            put("body", p.body)
        }
        return d.insertWithOnConflict("pending", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun pendings(): List<PendingTx> {
        val out = ArrayList<PendingTx>()
        readableDatabase.query("pending", null, null, null, null, null, "ts DESC").use { c ->
            while (c.moveToNext()) {
                out.add(
                    PendingTx(
                        id = c.lng("id"),
                        hash = c.str("hash"),
                        accountId = c.lng("account_id"),
                        sender = c.str("sender"),
                        type = TxType.valueOf(c.str("type")),
                        amount = c.lng("amount"),
                        balance = c.lngOrNull("balance"),
                        merchant = c.str("merchant"),
                        category = c.str("category"),
                        sub = c.str("sub"),
                        ts = c.lng("ts"),
                        body = c.str("body"),
                    )
                )
            }
        }
        return out
    }

    fun deletePending(id: Long) {
        writableDatabase.execSQL("DELETE FROM pending WHERE id = ?", arrayOf<Any>(id))
    }

    // ---------------------------------------------------------------- دسته‌ها

    fun categories(): List<Category> {
        val out = ArrayList<Category>()
        readableDatabase.query("category", null, null, null, null, null, "sort ASC, id ASC").use { c ->
            while (c.moveToNext()) {
                out.add(
                    Category(
                        id = c.lng("id"),
                        name = c.str("name"),
                        type = TxType.valueOf(c.str("type")),
                        parentId = c.lng("parent_id"),
                        icon = c.str("icon"),
                        keywords = c.str("keywords"),
                    )
                )
            }
        }
        return out
    }

    fun addCategory(name: String, type: TxType, parentId: Long, icon: String, keywords: String): Long {
        val d = writableDatabase
        var next = 0
        d.rawQuery("SELECT COALESCE(MAX(sort), 0) + 1 FROM category", null).use { c ->
            if (c.moveToFirst()) next = c.getInt(0)
        }
        return insertCat(d, name, type.name, parentId, icon, keywords, next)
    }

    fun deleteCategory(id: Long) {
        writableDatabase.execSQL(
            "DELETE FROM category WHERE id = ? OR parent_id = ?",
            arrayOf<Any>(id, id),
        )
    }

    private fun splitKeywords(s: String): List<String> =
        Norm.normalize(s).lowercase().split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }

    fun catCtx(type: TxType): Categorizer.Ctx {
        val all = categories().filter { it.type == type }
        val tops = all.filter { it.parentId == 0L }
        val byId = tops.associateBy { it.id }
        val custom = ArrayList<Triple<String, String, List<String>>>()
        for (c in all) {
            if (c.keywords.isBlank()) continue
            val kws = splitKeywords(c.keywords)
            if (c.parentId == 0L) {
                custom.add(Triple(c.name, "", kws))
            } else {
                val parent = byId[c.parentId] ?: continue
                custom.add(Triple(parent.name, c.name, kws))
            }
        }
        // زیرشاخه‌ها دقیق‌ترند و اول بررسی می‌شوند
        custom.sortByDescending { it.second.isNotEmpty() }
        return Categorizer.Ctx(tops.map { it.name }.toCollection(LinkedHashSet()), custom)
    }

    // ---------------------------------------------------------------- چک، طلب، بدهی، قسط، وام

    private fun Cursor.toObligation(): Obligation = Obligation(
        id = lng("id"),
        kind = ObKind.valueOf(str("kind")),
        title = str("title"),
        party = str("party"),
        amount = lng("amount"),
        dueTs = lng("due_ts"),
        note = str("note"),
        done = int("done") == 1,
        repeat = int("rep") == 1,
        remaining = int("remaining"),
        created = lng("created"),
    )

    fun obligations(): List<Obligation> {
        val out = ArrayList<Obligation>()
        readableDatabase.query("obligation", null, null, null, null, null, "due_ts ASC").use { c ->
            while (c.moveToNext()) out.add(c.toObligation())
        }
        return out
    }

    fun obligation(id: Long): Obligation? {
        readableDatabase.query("obligation", null, "id = ?", arrayOf(id.toString()), null, null, null).use { c ->
            return if (c.moveToFirst()) c.toObligation() else null
        }
    }

    private fun obValues(o: Obligation) = ContentValues().apply {
        put("kind", o.kind.name)
        put("title", o.title)
        put("party", o.party)
        put("amount", o.amount)
        put("due_ts", o.dueTs)
        put("note", o.note)
        put("done", if (o.done) 1 else 0)
        put("rep", if (o.repeat) 1 else 0)
        put("remaining", o.remaining)
        put("created", o.created)
    }

    fun addObligation(o: Obligation): Long = writableDatabase.insert("obligation", null, obValues(o))

    fun updateObligation(o: Obligation) {
        writableDatabase.update("obligation", obValues(o), "id = ?", arrayOf(o.id.toString()))
    }

    fun setObligationDone(id: Long, done: Boolean) {
        writableDatabase.execSQL(
            "UPDATE obligation SET done = ? WHERE id = ?",
            arrayOf<Any>(if (done) 1 else 0, id),
        )
    }

    fun deleteObligation(id: Long) {
        writableDatabase.execSQL("DELETE FROM obligation WHERE id = ?", arrayOf<Any>(id))
    }

    // ---------------------------------------------------------------- پشتیبان

    private val backupTables = listOf("account", "sms_source", "category", "tx", "pending", "obligation")

    fun dump(): JSONObject {
        val root = JSONObject()
        root.put("version", 2)
        val tables = JSONObject()
        for (t in backupTables) {
            val arr = JSONArray()
            readableDatabase.query(t, null, null, null, null, null, null).use { c ->
                while (c.moveToNext()) {
                    val o = JSONObject()
                    for (i in 0 until c.columnCount) {
                        val n = c.getColumnName(i)
                        when (c.getType(i)) {
                            Cursor.FIELD_TYPE_NULL -> o.put(n, JSONObject.NULL)
                            Cursor.FIELD_TYPE_INTEGER -> o.put(n, c.getLong(i))
                            Cursor.FIELD_TYPE_FLOAT -> o.put(n, c.getDouble(i))
                            else -> o.put(n, c.getString(i))
                        }
                    }
                    arr.put(o)
                }
            }
            tables.put(t, arr)
        }
        root.put("tables", tables)
        return root
    }

    fun restore(root: JSONObject) {
        val d = writableDatabase
        d.beginTransaction()
        try {
            val tables = root.getJSONObject("tables")
            for (t in backupTables.reversed()) d.execSQL("DELETE FROM $t")
            for (t in backupTables) {
                val arr = tables.optJSONArray(t) ?: continue
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val cv = ContentValues()
                    val keys = o.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val v = o.get(k)
                        when {
                            v == JSONObject.NULL -> cv.putNull(k)
                            v is Int -> cv.put(k, v.toLong())
                            v is Long -> cv.put(k, v)
                            v is Double -> cv.put(k, v)
                            else -> cv.put(k, v.toString())
                        }
                    }
                    d.insert(t, null, cv)
                }
            }
            var accounts = 0
            d.rawQuery("SELECT COUNT(*) FROM account WHERE id = 1", null).use { c ->
                if (c.moveToFirst()) accounts = c.getInt(0)
            }
            var cats = 0
            d.rawQuery("SELECT COUNT(*) FROM category", null).use { c ->
                if (c.moveToFirst()) cats = c.getInt(0)
            }
            if (accounts == 0 || cats == 0) seedMissing(d, accounts == 0, cats == 0)
            d.setTransactionSuccessful()
        } finally {
            d.endTransaction()
        }
    }

    private fun seedMissing(d: SQLiteDatabase, cash: Boolean, cats: Boolean) {
        if (cash) d.execSQL("INSERT OR IGNORE INTO account(id, name, bank, kind) VALUES (1, 'نقدی', '', 'CASH')")
        if (cats) {
            var i = 0
            for (n in Categorizer.builtinExpense) insertCat(d, n, "EXPENSE", 0, Look.catIcon[n] ?: "🧾", "", i++)
            for (n in Categorizer.builtinIncome) insertCat(d, n, "INCOME", 0, Look.catIcon[n] ?: "💰", "", i++)
        }
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
