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

    init {
        viewModelScope.launch {
            Store.changes.collect {
                loadMonth()
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

    private suspend fun runSearch() {
        val q = Norm.normalize(query)
        results = if (q.isBlank()) emptyList() else withContext(Dispatchers.IO) { db.search(q) }
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
