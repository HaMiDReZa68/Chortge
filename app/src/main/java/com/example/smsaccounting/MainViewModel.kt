package com.example.smsaccounting

import android.app.Application
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
    var banks by mutableStateOf<List<BankInfo>>(emptyList())
        private set
    var cashNet by mutableStateOf(0L)
        private set
    var budgets by mutableStateOf(prefs.budgets())
        private set
    var cashInitial by mutableStateOf(prefs.cashInitial)
        private set
    var cashBalance by mutableStateOf(0L)
        private set
    var bankBalance by mutableStateOf<Map<String, Long>>(emptyMap())
        private set
    var recent by mutableStateOf<List<Tx>>(emptyList())
        private set
    var todaySpent by mutableStateOf(0L)
        private set
    var budgets by mutableStateOf(prefs.budgets())
        private set

    init {
        viewModelScope.launch {
            Store.changes.collect {
                loadMonth()
                loadBanks()
                if (query.isNotBlank()) runSearch()
            }
        }
    }

    private suspend fun loadMonth() {
        val ym = month
        val (from, to) = Jalali.range(ym)
        val (pf, pt) = Jalali.range(ym.prev())
        val dis = prefs.disabledSenders()
        val list = withContext(Dispatchers.IO) { db.query(from, to) }.filter { it.sender !in dis }
        val prev = withContext(Dispatchers.IO) { db.query(pf, pt) }.filter { it.sender !in dis }
        if (ym == month) {
            items = list
            prevIncome = prev.filter { it.type == TxType.INCOME }.sumOf { it.amount }
            prevExpense = prev.filter { it.type == TxType.EXPENSE }.sumOf { it.amount }
        }
    }

    private suspend fun runSearch() {
        val q = Norm.normalize(query)
        val dis = prefs.disabledSenders()
        results = if (q.isBlank()) emptyList()
        else withContext(Dispatchers.IO) { db.search(q) }.filter { it.sender !in dis }
    }

    private suspend fun loadBanks() {
        val dis = prefs.disabledSenders()
        val rows = withContext(Dispatchers.IO) { db.senders() }
        val bal = withContext(Dispatchers.IO) { db.lastBalances() }
        cashNet = withContext(Dispatchers.IO) { db.cashNet() }
        banks = rows.map { (s, n) ->
            BankInfo(s, prefs.bankName(s) ?: BankNames.guess(s), n, s !in dis, bal[s])
        }

        val (inc, exp) = withContext(Dispatchers.IO) { db.cashSums() }
        cashInitial = prefs.cashInitial
        cashBalance = prefs.cashInitial + inc - exp
        bankBalance = withContext(Dispatchers.IO) { db.lastBalances() }
        recent = withContext(Dispatchers.IO) { db.recent(40) }.filter { it.sender !in dis }.take(10)

        val c = java.util.Calendar.getInstance()
        c.set(java.util.Calendar.HOUR_OF_DAY, 0)
        c.set(java.util.Calendar.MINUTE, 0)
        c.set(java.util.Calendar.SECOND, 0)
        c.set(java.util.Calendar.MILLISECOND, 0)
        val start = c.timeInMillis
        todaySpent = withContext(Dispatchers.IO) { db.query(start, start + 86_400_000L) }
            .filter { it.sender !in dis && it.type == TxType.EXPENSE }
            .sumOf { it.amount }
    }

    fun setCashInitial(rial: Long) {
        prefs.cashInitial = rial
        Store.notifyChanged()
    }

    fun setBudget(category: String, rial: Long) {
        prefs.setBudget(category, rial)
        budgets = prefs.budgets()
    }

    fun applyReminder(on: Boolean, h: Int, m: Int) {
        prefs.reminderOn = on
        prefs.reminderH = h
        prefs.reminderM = m
        reminderOn = on
        reminderH = h
        reminderM = m
        val app = getApplication<Application>()
        if (on) Reminder.schedule(app) else Reminder.cancel(app)
    }

    fun setBudget(category: String, rial: Long) {
        prefs.setBudget(category, rial)
        budgets = prefs.budgets()
    }

    fun toggleBank(sender: String, enabled: Boolean) {
        prefs.setSenderEnabled(sender, enabled)
        Store.notifyChanged()
        if (enabled) importSms(true)
    }

    fun renameBank(sender: String, name: String) {
        prefs.setBankName(sender, name.trim())
        Store.notifyChanged()
    }

    fun addCash(type: TxType, amountRial: Long, note: String, category: String) {
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
                    )
                )
            }
            month = Jalali.ymOf(now)
            status = "تراکنش نقدی ثبت شد"
            Store.notifyChanged()
        }
    }

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

    fun changeToman(v: Boolean) {
        toman = v
        prefs.toman = v
    }

    fun clearStatus() {
        status = null
    }

    /** all = true یعنی همهٔ پیامک‌ها از اول بررسی شود */
    fun importSms(all: Boolean) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            val n = try {
                withContext(Dispatchers.IO) { Importer.importInbox(getApplication<Application>(), all) }
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

    fun setCategory(tx: Tx, category: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.updateCategory(tx.id, category)
            if (tx.type == TxType.EXPENSE && tx.mkey.isNotEmpty()) {
                prefs.learn(tx.mkey, category)
                db.updateCategoryByMkey(tx.mkey, category)
            }
            Store.notifyChanged()
        }
    }

    fun toggleType(tx: Tx) {
        viewModelScope.launch(Dispatchers.IO) {
            if (tx.type == TxType.EXPENSE) {
                db.updateType(tx.id, TxType.INCOME, Categorizer.INCOME)
            } else {
                db.updateType(tx.id, TxType.EXPENSE, Categorizer.OTHER)
            }
            Store.notifyChanged()
        }
    }

    fun remove(tx: Tx) {
        viewModelScope.launch(Dispatchers.IO) {
            db.softDelete(tx.id)
            Store.notifyChanged()
        }
    }
}
