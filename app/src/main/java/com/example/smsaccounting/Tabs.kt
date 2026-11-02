package com.example.smsaccounting

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val navItems = listOf("🏠" to "خانه", "📊" to "گزارش", "🐷" to "بودجه")

@Composable
fun BottomNav(tab: Int, onTab: (Int) -> Unit, modifier: Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
    ) {
        Row(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            navItems.forEachIndexed { i, (icon, label) ->
                val sel = i == tab
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (sel) Teal1.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { onTab(i) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(icon, fontSize = 20.sp)
                    Text(
                        label,
                        fontSize = 11.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                        color = if (sel) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountCard(icon: String, name: String, value: String, enabled: Boolean) {
    Column(
        Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = if (enabled) 0.95f else 0.55f))
            .padding(14.dp)
    ) {
        Text(icon, fontSize = 22.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            name,
            fontSize = 12.sp,
            color = Color(0xFF4B5B57),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (enabled) value else "غیرفعال",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF12201C),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun HomeTab(vm: MainViewModel, onSettings: () -> Unit, onPick: (Tx) -> Unit) {
    val unit = if (vm.toman) "تومان" else "ریال"
    fun show(v: Long) = Fa.money(if (vm.toman) v / 10 else v)
    val total = vm.cashNet + vm.banks.filter { it.enabled }.sumOf { it.balance ?: 0L }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 160.dp)) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                    .background(Brush.verticalGradient(listOf(Teal1, Teal2)))
                    .statusBarsPadding()
                    .padding(top = 16.dp, bottom = 22.dp),
            ) {
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "خانه",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.weight(1f),
                    )
                    RoundBtn("⚙", onSettings)
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    "دارایی کل",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.Bottom) {
                    Text(
                        (if (total < 0) "−" else "") + show(kotlin.math.abs(total)),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 34.sp,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        unit,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                Text(
                    "بر اساس آخرین مانده در پیامک هر بانک و تراکنش‌های نقدی",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AccountCard("💵", "نقدی", show(vm.cashNet) + " " + unit, true)
                    vm.banks.forEach { b ->
                        val bal = b.balance
                        AccountCard(
                            "🏦",
                            b.name,
                            if (bal != null) show(bal) + " " + unit else "مانده نامشخص",
                            b.enabled,
                        )
                    }
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
        items(vm.items.take(6), key = { it.id }) { tx ->
            TxRow(tx, show(tx.amount), unit) { onPick(tx) }
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
    val cats = Categorizer.expenseCategories
    val limited = cats.filter { (vm.budgets[it] ?: 0L) > 0L }
    val totalLimit = limited.sumOf { vm.budgets[it] ?: 0L }
    val totalSpent = limited.sumOf { spent[it] ?: 0L }
    val remain = totalLimit - totalSpent

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 160.dp)) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                    .background(Brush.verticalGradient(listOf(Teal1, Teal2)))
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Text("بودجه‌بندی", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    RoundBtn("›", vm::prevMonth)
                    Text(
                        Jalali.title(vm.month),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                    )
                    RoundBtn("‹", vm::nextMonth)
                }
                Spacer(Modifier.height(16.dp))
                if (limited.isEmpty()) {
                    Text(
                        "برای هر دسته یک سقف ماهانه تعیین کن تا خرجت دستت باشد.",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        if (remain >= 0) "باقی‌مانده از بودجه" else "بیشتر از بودجه",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Text(
                        show(kotlin.math.abs(remain)) + " " + unit,
                        color = Color.White,
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
                            .background(Color.White.copy(alpha = 0.25f))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(frac.coerceAtLeast(0.02f))
                                .height(8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.White)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        show(totalSpent) + " از " + show(totalLimit) + " " + unit,
                        color = Color.White.copy(alpha = 0.85f),
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
                shadowElevation = 1.dp,
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
