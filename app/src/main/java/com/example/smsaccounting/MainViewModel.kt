package com.example.smsaccounting

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val db = Db.get(app)
    private val prefs = Prefs(app)
    private val ctx get() = getApplication<Application>()

    var month by mutableStateOf(Jalali.ymOf(System.currentTimeMillis()))
        private set
    var items by mutableStateOf<List<Tx>>(emptyList())
        private set
    var toman by mutableStateOf(prefs.toman)
        private set
    var status by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var mode by mutableStateOf(TxType.EXPENSE)
        private set
    var prevIncome by mutableStateOf(0L)
        private set
    var prevExpense by mutableStateOf(0L)
        private set
    var searchOpen by mutableStateOf(false)
        private set
    var query by mutableStateOf("")
        private set
    var results by mutableStateOf<List<Tx>>(emptyList())
        private set
    var reminderOn by mutableStateOf(prefs.reminderOn)
        private set
    var reminderH by mutableStateOf(prefs.reminderH)
        private set
    var reminderM by mutableStateOf(prefs.reminderM)
        private set
    var cashNet by mutableStateOf(0L)
        private set
    var budgets by mutableStateOf(prefs.budgets())
        private set
    var accounts by mutableStateOf<List<Account>>(emptyList())
        private set
    var sources by mutableStateOf<List<SmsSource>>(emptyList())
        private set
    var cats by mutableStateOf<List<Category>>(emptyList())
        private set
    var pendings by mutableStateOf<List<PendingTx>>(emptyList())
        private set
    var obligations by mutableStateOf<List<Obligation>>(emptyList())
        private set
    var candidates by mutableStateOf<List<SenderCandidate>>(emptyList())
        private set
    var scanning by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            Store.changes.collect {
                loadMonth()
                loadMeta()
                if (query.isNotBlank()) runSearch()
            }
        }
    }

    private suspend fun loadMonth() {
        val ym = month
        val (from, to) = Jalali.range(ym)
        val (pf, pt) = Jalali.range(ym.prev())
        val list = withContext(Dispatchers.IO) { db.query(from, to) }
        val prev = withContext(Dispatchers.IO) { db.query(pf, pt) }
        if (ym == month) {
            items = list
            prevIncome = prev.filter { it.type == TxType.INCOME }.sumOf { it.amount }
            prevExpense = prev.filter { it.type == TxType.EXPENSE }.sumOf { it.amount }
        }
    }

    private suspend fun loadMeta() {
        withContext(Dispatchers.IO) {
            val a = db.accounts()
            val s = db.sources()
            val c = db.categories()
            val p = db.pendings()
            val o = db.obligations()
            val cn = db.cashNet()
            Look.setCustomIcons(c.associate { it.name to it.icon })
            withContext(Dispatchers.Main) {
                accounts = a
                sources = s
                cats = c
                pendings = p
                obligations = o
                cashNet = cn
            }
        }
    }

    private suspend fun runSearch() {
        val q = Norm.normalize(query)
        results = if (q.isBlank()) emptyList() else withContext(Dispatchers.IO) { db.search(q) }
    }

    private fun io(block: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            block()
            Store.notifyChanged()
        }
    }

    fun say(s: String) {
        status = s
    }

    // ---------------------------------------------------------------- تنظیمات

    fun applyReminder(on: Boolean, h: Int, m: Int) {
        prefs.reminderOn = on
        prefs.reminderH = h
        prefs.reminderM = m
        reminderOn = on
        reminderH = h
        reminderM = m
        if (on) Reminder.schedule(ctx) else Reminder.cancel(ctx)
    }

    fun setBudget(category: String, rial: Long) {
        prefs.setBudget(category, rial)
        budgets = prefs.budgets()
    }

    fun changeToman(v: Boolean) {
        toman = v
        prefs.toman = v
    }

    fun clearStatus() {
        status = null
    }

    // ---------------------------------------------------------------- نمایش

    fun changeMode(m: TxType) {
        mode = m
    }

    fun toggleSearch() {
        searchOpen = !searchOpen
        if (!searchOpen) {
            query = ""
            results = emptyList()
        }
    }

    fun search(q: String) {
        query = q
        viewModelScope.launch { runSearch() }
    }

    private fun changeMonth(ym: Jalali.YM) {
        month = ym
        viewModelScope.launch { loadMonth() }
    }

    fun prevMonth() = changeMonth(month.prev())
    fun nextMonth() = changeMonth(month.next())

    // ---------------------------------------------------------------- حساب‌ها و سرشماره‌ها

    fun scanSenders() {
        if (scanning) return
        viewModelScope.launch {
            scanning = true
            candidates = try {
                withContext(Dispatchers.IO) { Importer.scanSenders(ctx) }
            } catch (e: Exception) {
                emptyList()
            }
            scanning = false
        }
    }

    /** حساب جدید + اتصال سرشماره + (اختیاری) خواندن پیامک‌های قدیمی از تاریخ مشخص */
    fun createAccount(name: String, bank: String, sender: String, match: String, sinceMs: Long?) {
        viewModelScope.launch {
            busy = true
            val n = withContext(Dispatchers.IO) {
                val id = db.addAccount(name.trim(), bank.trim())
                if (sender.isNotBlank()) db.addSource(sender, id, match.trim())
                if (sinceMs != null && sender.isNotBlank()) {
                    try {
                        Importer.importForAccount(ctx, id, sinceMs)
                    } catch (e: Exception) {
                        -1
                    }
                } else 0
            }
            busy = false
            status = when {
                n < 0 -> "حساب ساخته شد ولی خواندن پیامک‌ها ممکن نشد"
                n > 0 -> "حساب ساخته شد و ${Fa.digits(n.toString())} تراکنش خوانده شد"
                else -> "حساب ساخته شد"
            }
            Store.notifyChanged()
        }
    }

    fun importAccount(accountId: Long, sinceMs: Long) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            val n = try {
                withContext(Dispatchers.IO) { Importer.importForAccount(ctx, accountId, sinceMs) }
            } catch (e: Exception) {
                -1
            }
            busy = false
            status = when {
                n < 0 -> "خواندن پیامک‌ها ممکن نشد"
                n > 0 -> "${Fa.digits(n.toString())} تراکنش جدید ثبت شد"
                else -> "تراکنش جدیدی پیدا نشد"
            }
            Store.notifyChanged()
        }
    }

    fun renameAccount(id: Long, name: String, bank: String) = io { db.updateAccount(id, name.trim(), bank.trim()) }

    fun deleteAccount(id: Long) = io { db.deleteAccount(id) }

    fun addSource(sender: String, accountId: Long, match: String) =
        io { db.addSource(sender.trim(), accountId, match.trim()) }

    fun removeSource(id: Long) = io { db.removeSource(id) }

    // ---------------------------------------------------------------- در انتظار ثبت

    fun registerPending(p: PendingTx, type: TxType, amountRial: Long, category: String, sub: String, note: String) {
        io {
            val tx = Tx(
                hash = p.hash,
                sender = p.sender,
                type = type,
                amount = amountRial,
                balance = p.balance,
                merchant = p.merchant,
                mkey = Categorizer.mkey(p.merchant),
                category = category,
                ts = p.ts,
                body = p.body,
                accountId = p.accountId,
                sub = sub,
                note = note.trim(),
            )
            db.insert(tx)
            if (type == TxType.EXPENSE && tx.mkey.isNotEmpty()) prefs.learn(tx.mkey, "$category|$sub")
            db.deletePending(p.id)
        }
    }

    fun dismissPending(p: PendingTx) = io { db.deletePending(p.id) }

    // ---------------------------------------------------------------- دسته‌ها

    fun addCategory(name: String, type: TxType, parentId: Long, icon: String, keywords: String) =
        io { db.addCategory(name.trim(), type, parentId, icon.trim(), keywords.trim()) }

    fun deleteCategory(id: Long) = io { db.deleteCategory(id) }

    // ---------------------------------------------------------------- تراکنش‌ها

    fun saveTx(tx: Tx, type: TxType, category: String, sub: String, note: String) {
        io {
            db.updateTx(tx.id, type, category, sub, note.trim())
            if (type == TxType.EXPENSE && tx.mkey.isNotEmpty()) {
                prefs.learn(tx.mkey, "$category|$sub")
                db.updateCategoryByMkey(tx.mkey, category, sub)
            }
        }
    }

    fun remove(tx: Tx) = io { db.softDelete(tx.id) }

    fun addCash(type: TxType, amountRial: Long, note: String, category: String, sub: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val n = note.trim()
            withContext(Dispatchers.IO) {
                db.insert(
                    Tx(
                        hash = sha1("cash|$now|${System.nanoTime()}"),
                        sender = "cash",
                        type = type,
                        amount = amountRial,
                        balance = null,
                        merchant = n,
                        mkey = Categorizer.mkey(n),
                        category = category,
                        ts = now,
                        body = if (n.isEmpty()) "تراکنش نقدی" else "تراکنش نقدی: $n",
                        accountId = 1,
                        sub = sub,
                        note = n,
                    )
                )
            }
            month = Jalali.ymOf(now)
            status = "تراکنش نقدی ثبت شد"
            Store.notifyChanged()
        }
    }

    /** پیشنهاد دسته برای متن آزاد (ثبت نقدی) */
    fun suggest(type: TxType, text: String): Categorizer.Result =
        Categorizer.categorize(type, text, text, prefs.learned(), db.catCtx(type))

    // ---------------------------------------------------------------- چک، طلب، بدهی، قسط، وام

    fun saveObligation(o: Obligation) {
        io {
            if (o.id == 0L) {
                val id = db.addObligation(o)
                Alarms.schedule(ctx, o.copy(id = id))
            } else {
                db.updateObligation(o)
                Alarms.cancel(ctx, o.id)
                Alarms.schedule(ctx, o)
            }
        }
    }

    fun deleteObligation(o: Obligation) {
        io {
            Alarms.cancel(ctx, o.id)
            db.deleteObligation(o.id)
        }
    }

    /** انجام‌شده؛ قسط و وام تکرارشونده به ماه بعد می‌رود */
    fun finishObligation(o: Obligation) {
        io {
            Alarms.cancel(ctx, o.id)
            if (o.repeat) {
                val left = if (o.remaining > 0) o.remaining - 1 else 0
                if (o.remaining == 1) {
                    db.setObligationDone(o.id, true)
                } else {
                    val n = o.copy(dueTs = Jalali.addMonths(o.dueTs, 1), remaining = left)
                    db.updateObligation(n)
                    Alarms.schedule(ctx, n)
                }
            } else {
                db.setObligationDone(o.id, true)
            }
        }
    }

    fun reopenObligation(o: Obligation) {
        io {
            db.setObligationDone(o.id, false)
            Alarms.schedule(ctx, o.copy(done = false))
        }
    }

    // ---------------------------------------------------------------- خروجی و پشتیبان

    private suspend fun exportBytes(spec: ExportSpec, xlsx: Boolean): ByteArray = withContext(Dispatchers.IO) {
        val t = Export.table(ctx, spec)
        if (xlsx) Export.xlsx(t) else Export.pdf(ctx, t)
    }

    fun exportName(spec: ExportSpec, xlsx: Boolean): String =
        (if (spec.kind == 0) "transactions-" else "income-expense-") +
            Jalali.ymd(spec.from).replace('/', '-') + "_" + Jalali.ymd(spec.to - 1).replace('/', '-') +
            if (xlsx) ".xlsx" else ".pdf"

    fun saveExport(spec: ExportSpec, xlsx: Boolean, uri: Uri) {
        viewModelScope.launch {
            busy = true
            val ok = try {
                Share.write(ctx, uri, exportBytes(spec, xlsx))
            } catch (e: Exception) {
                false
            }
            busy = false
            status = if (ok) "فایل ذخیره شد" else "ذخیرهٔ فایل ممکن نشد"
        }
    }

    fun shareExport(spec: ExportSpec, xlsx: Boolean) {
        viewModelScope.launch {
            busy = true
            try {
                val b = exportBytes(spec, xlsx)
                Share.send(
                    ctx, exportName(spec, xlsx),
                    if (xlsx) "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" else "application/pdf",
                    b,
                )
            } catch (e: Exception) {
                status = "ساخت فایل ممکن نشد"
            }
            busy = false
        }
    }

    fun saveBackup(uri: Uri) {
        viewModelScope.launch {
            busy = true
            val ok = withContext(Dispatchers.IO) { Share.write(ctx, uri, Backup.toBytes(ctx)) }
            busy = false
            status = if (ok) "نسخهٔ پشتیبان ذخیره شد" else "ذخیرهٔ پشتیبان ممکن نشد"
        }
    }

    fun shareBackup() {
        viewModelScope.launch {
            busy = true
            val b = withContext(Dispatchers.IO) { Backup.toBytes(ctx) }
            Share.send(ctx, Backup.fileName(), "application/json", b)
            busy = false
        }
    }

    fun restoreBackup(uri: Uri) {
        viewModelScope.launch {
            busy = true
            val ok = withContext(Dispatchers.IO) {
                val b = Share.read(ctx, uri)
                b != null && Backup.restore(ctx, b)
            }
            busy = false
            if (ok) {
                toman = prefs.toman
                reminderOn = prefs.reminderOn
                reminderH = prefs.reminderH
                reminderM = prefs.reminderM
                budgets = prefs.budgets()
            }
            status = if (ok) "اطلاعات بازیابی شد" else "فایل پشتیبان معتبر نیست"
        }
    }
}
