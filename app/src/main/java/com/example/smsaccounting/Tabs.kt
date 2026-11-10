package com.example.smsaccounting

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val navItems = listOf(
    "list" to "دسته‌بندی", "home" to "خانه", "chart" to "گزارش", "wallet" to "بودجه", "clock" to "سررسید",
)

@Composable
fun BottomNav(tab: Int, badge: Int, onTab: (Int) -> Unit, modifier: Modifier, onAdd: (() -> Unit)? = null) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val pillColor = if (dark) Color(0xFF2A333F) else Color(0xFF111318)
    Row(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(52.dp)
                .shadow(8.dp, RoundedCornerShape(30.dp))
                .clip(RoundedCornerShape(30.dp))
                .background(pillColor)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            navItems.forEachIndexed { i, (icon, _) ->
                val sel = i == tab
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (sel) Color.White else Color.Transparent)
                        .clickable { onTab(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    LineIcon(icon, if (sel) Color(0xFF111318) else Color.White, 22.dp)
                    if (i == 0 && badge > 0) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .clip(CircleShape)
                                .background(Red)
                                .padding(horizontal = 4.dp),
                        ) { Text(Fa.digits(badge.toString()), color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
        if (onAdd != null) {
            Box(
                Modifier
                    .size(48.dp)
                    .shadow(8.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Teal1)
                    .clickable { onAdd() },
                contentAlignment = Alignment.Center,
            ) { LineIcon("plus", Color.White, 24.dp) }
        }
    }
}

@Composable
private fun AccountCard(icon: String, name: String, value: String) {
    Column(
        Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp)
    ) {
        Text(icon, fontSize = 20.sp)
        Spacer(Modifier.height(6.dp))
        Text(name, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun HomeTab(vm: MainViewModel, onSettings: () -> Unit, onNav: (Page) -> Unit, onTab: (Int) -> Unit, onPick: (Tx) -> Unit) {
    val unit = if (vm.toman) "تومان" else "ریال"
    fun show(v: Long) = Fa.money(if (vm.toman) v / 10 else v)
    val banks = vm.accounts.filter { !it.isCash }
    val total = vm.accounts.sumOf { it.lastBalance ?: 0L }
    var homeTab by remember { mutableStateOf(0) }
    val upcoming = vm.obligations.filter { !it.done }.sortedBy { it.dueTs }.take(3)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 160.dp)) {
        item {
            Column {
                TopBar(logo = true) {
                    RoundBtn(if (vm.searchOpen) "✕" else "🔍", vm::toggleSearch)
                    Spacer(Modifier.width(8.dp))
                    RoundBtn("⚙", onSettings)
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("دارایی کل", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    Text(unit, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        (if (total < 0) "−" else "") + show(kotlin.math.abs(total)),
                        color = if (total < 0) Red else Green,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                    )
                }
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AccountCard("💵", "نقدی", show(vm.cashNet) + " " + unit)
                    banks.forEach { b ->
                        val bal = b.lastBalance
                        AccountCard("🏦", b.name + if (b.bank.isNotBlank()) " · " + b.bank else "", if (bal != null) show(bal) + " " + unit else "—")
                    }
                }
                Spacer(Modifier.height(6.dp))
                UnderlineTabs(listOf("همه", "درآمد", "هزینه", "انتقال"), homeTab) { homeTab = it }
            }
        }

        if (vm.searchOpen) {
            item {
                OutlinedTextField(
                    value = vm.query,
                    onValueChange = vm::search,
                    placeholder = { Text("جستجو در شرح، دسته، توضیحات یا متن پیامک") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )
            }
            items(vm.results, key = { it.id }) { tx ->
                TxRow(tx, show(tx.amount), unit, withDate = true, account = vm.accName(tx.accountId)) { onPick(tx) }
            }
            return@LazyColumn
        }

        vm.update?.let { u ->
            item {
                Row(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFD9F2E3))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("نسخهٔ جدید آماده است", color = Color(0xFF14532D), fontWeight = FontWeight.Bold)
                        Text(u.name, color = Color(0xFF14532D), fontSize = 11.sp)
                    }
                    SoftButton("دانلود") { vm.installUpdate() }
                }
            }
        }

        item {
            Row(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { onNav(Page.INVOICES) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🧾  فاکتورها", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("‹", fontSize = 20.sp)
            }
        }

        if (banks.isEmpty()) {
            item {
                SectionCard(Modifier.padding(16.dp).clickable { onNav(Page.ADD_ACCOUNT) }) {
                    Text("🏦 بانک اضافه کن", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Hint("حساب را بساز و سرشمارهٔ پیامک بانک را انتخاب کن تا از این به بعد با هر پیامک اعلان بگیری.")
                }
            }
        }

        if (vm.pendings.isNotEmpty()) {
            item {
                Row(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFFFF4D6))
                        .clickable { onTab(0) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        Fa.digits(vm.pendings.size.toString()) + " تراکنش جدید منتظر ثبت بابت است",
                        color = Color(0xFF5B4A00),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text("ثبت ›", color = Color(0xFF5B4A00), fontWeight = FontWeight.Bold)
                }
            }
        }

        if (upcoming.isNotEmpty()) {
            item { SectionTitle("سررسیدهای نزدیک") }
            items(upcoming, key = { "ob_" + it.id }) { o ->
                Row(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onTab(4) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(o.kind.icon, fontSize = 20.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(o.kind.label + ": " + o.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            Jalali.dateTime(o.dueTs),
                            fontSize = 11.sp,
                            color = if (o.dueTs < System.currentTimeMillis()) Red else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (o.amount > 0) Text(show(o.amount), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        item { SectionTitle("آخرین تراکنش‌ها") }
        if (vm.items.isEmpty()) {
            item {
                Text(
                    "در این ماه تراکنشی ثبت نشده است.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                )
            }
        }
        val shown = vm.items.filter {
            when (homeTab) {
                1 -> it.type == TxType.INCOME
                2 -> it.type == TxType.EXPENSE
                3 -> it.type.isTransfer
                else -> true
            }
        }
        items(shown.take(10), key = { it.id }) { tx ->
            TxRow(tx, show(tx.amount), unit, account = vm.accName(tx.accountId)) { onPick(tx) }
        }
    }
}

@Composable
fun BudgetTab(vm: MainViewModel) {
    val unit = if (vm.toman) "تومان" else "ریال"
    fun show(v: Long) = Fa.money(if (vm.toman) v / 10 else v)
    var editing by remember { mutableStateOf<String?>(null) }

    val spent = vm.items
        .filter { it.type == TxType.EXPENSE }
        .groupBy { it.category }
        .mapValues { e -> e.value.sumOf { it.amount } }
    val cats = vm.cats.filter { it.type == TxType.EXPENSE && it.parentId == 0L }.map { it.name }
    val limited = cats.filter { (vm.budgets[it] ?: 0L) > 0L }
    val totalLimit = limited.sumOf { vm.budgets[it] ?: 0L }
    val totalSpent = limited.sumOf { spent[it] ?: 0L }
    val remain = totalLimit - totalSpent

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 160.dp)) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Text("بودجه‌بندی", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    RoundBtn("›", vm::prevMonth)
                    Text(
                        Jalali.title(vm.month),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                    )
                    RoundBtn("‹", vm::nextMonth)
                }
                Spacer(Modifier.height(16.dp))
                if (limited.isEmpty()) {
                    Text(
                        "برای هر دسته یک سقف ماهانه تعیین کن تا خرجت دستت باشد.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        if (remain >= 0) "باقی‌مانده از بودجه" else "بیشتر از بودجه",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Text(
                        show(kotlin.math.abs(remain)) + " " + unit,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Spacer(Modifier.height(10.dp))
                    val frac = (totalSpent.toFloat() / totalLimit.toFloat()).coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(frac.coerceAtLeast(0.02f))
                                .height(8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Teal1)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        show(totalSpent) + " از " + show(totalLimit) + " " + unit,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }
        }

        item { SectionTitle("سقف هر دسته") }

        items(cats, key = { it }) { c ->
            val limit = vm.budgets[c] ?: 0L
            val s = spent[c] ?: 0L
            val over = limit > 0 && s > limit
            val color = if (over) Red else Look.color(c)
            Surface(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { editing = c },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 0.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CatIcon(c, 44)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Text(
                                if (limit > 0) show(s) + " / " + show(limit) else show(s),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(RoundedCornerShape(50))
                                .background(color.copy(alpha = 0.15f))
                        ) {
                            if (limit > 0) {
                                val f = (s.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
                                Box(
                                    Modifier
                                        .fillMaxWidth(f.coerceAtLeast(0.02f))
                                        .height(7.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(color)
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            when {
                                limit <= 0L -> "سقف تعیین نشده؛ برای تنظیم بزن"
                                over -> show(s - limit) + " " + unit + " بیشتر از سقف"
                                else -> show(limit - s) + " " + unit + " باقی‌مانده"
                            },
                            fontSize = 11.sp,
                            color = if (over) Red else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    val cat = editing
    if (cat != null) {
        val cur = vm.budgets[cat] ?: 0L
        var text by remember(cat) {
            mutableStateOf(if (cur > 0) (if (vm.toman) cur / 10 else cur).toString() else "")
        }
        AlertDialog(
            onDismissRequest = { editing = null },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("سقف ماهانهٔ $cat", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("مبلغ ($unit)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(16.dp),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = Norm.normalize(text).replace(",", "").toLongOrNull() ?: 0L
                    vm.setBudget(cat, if (vm.toman) v * 10 else v)
                    editing = null
                }) { Text("ذخیره") }
            },
            dismissButton = {
                Row {
                    if (cur > 0) {
                        TextButton(onClick = {
                            vm.setBudget(cat, 0L)
                            editing = null
                        }) { Text("حذف سقف", color = Red) }
                    }
                    TextButton(onClick = { editing = null }) { Text("انصراف") }
                }
            },
        )
    }
}
