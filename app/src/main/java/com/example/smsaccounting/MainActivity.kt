package com.example.smsaccounting

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

private val Vazir = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
    Font(R.font.vazirmatn_bold, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.SemiBold),
)

internal val Teal1 = Color(0xFF0F766E)
internal val Teal2 = Color(0xFF14B8A6)
internal val Green = Color(0xFF16A34A)
internal val Red = Color(0xFFE11D48)

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val openReq = mutableStateOf<String?>(null)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        openReq.value = intent.getStringExtra("open")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openReq.value = intent?.getStringExtra("open")
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            AppTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Screen(vm, openReq.value) { openReq.value = null }
                }
            }
        }
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) {
        darkColorScheme(
            primary = Color(0xFF5EEAD4),
            background = Color(0xFF0E1513),
            surface = Color(0xFF17201D),
            onSurface = Color(0xFFE6F0ED),
            onSurfaceVariant = Color(0xFF9DB0AA),
        )
    } else {
        lightColorScheme(
            primary = Teal1,
            background = Color(0xFFF3F6F5),
            surface = Color.White,
            onSurface = Color(0xFF12201C),
            onSurfaceVariant = Color(0xFF6B7C77),
        )
    }
    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography.withFont(Vazir),
        content = content,
    )
}

internal fun titleOf(tx: Tx): String =
    tx.merchant.ifBlank { tx.body.replace('\n', ' ').take(40) }

@Composable
fun Screen(vm: MainViewModel, openReq: String?, onOpenHandled: () -> Unit) {
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.status) {
        val s = vm.status
        if (s != null) {
            snack.showSnackbar(s)
            vm.clearStatus()
        }
    }

    var selected by remember { mutableStateOf<Tx?>(null) }
    var page by remember { mutableStateOf<Page?>(null) }
    var tab by remember { mutableStateOf(0) }
    LaunchedEffect(openReq) {
        when (openReq) {
            "pending" -> page = Page.PENDING
            "obligations" -> {
                page = null
                tab = 3
            }
        }
        if (openReq != null) onOpenHandled()
    }
    BackHandler(page != null) {
        page = if (page == Page.ADD_ACCOUNT) Page.ACCOUNTS else null
    }
    var showAdd by remember { mutableStateOf(false) }

    val list = vm.items
    val income = list.filter { it.type == TxType.INCOME }.sumOf { it.amount }
    val expense = list.filter { it.type == TxType.EXPENSE }.sumOf { it.amount }
    val isExp = vm.mode == TxType.EXPENSE
    val modeList = list.filter { it.type == vm.mode }
    val modeTotal = modeList.sumOf { it.amount }
    val byCat = modeList
        .groupBy { it.category }
        .map { (k, v) -> k to v.sumOf { it.amount } }
        .sortedByDescending { it.second }
    val byDay = list.groupBy { Jalali.dayLabel(it.ts) }

    fun show(v: Long) = Fa.money(if (vm.toman) v / 10 else v)
    val unit = if (vm.toman) "تومان" else "ریال"
    val diff = income - expense

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (tab == 1) LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 160.dp),
        ) {
            // ---- هدر گرادیانی ----
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                        .background(Brush.verticalGradient(listOf(Teal1, Teal2)))
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "حسابداری پیامک",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.weight(1f),
                        )
                        RoundBtn(if (vm.searchOpen) "✕" else "🔍", vm::toggleSearch)
                        Spacer(Modifier.width(8.dp))
                        RoundBtn("⚙", { page = Page.SETTINGS })
                    }

                    Spacer(Modifier.height(18.dp))

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

                    Spacer(Modifier.height(20.dp))

                    Text(
                        "تراز این ماه",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Row(
                        Modifier.align(Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            (if (diff < 0) "−" else "") + show(kotlin.math.abs(diff)),
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

                    Spacer(Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MiniStat("درآمد", show(income), "↓", Modifier.weight(1f))
                        MiniStat("هزینه", show(expense), "↑", Modifier.weight(1f))
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(
                        Modifier.align(Alignment.CenterHorizontally),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Pill("تومان", filled = vm.toman, onClick = { vm.changeToman(true) })
                        Pill("ریال", filled = !vm.toman, onClick = { vm.changeToman(false) })
                    }
                }
            }

            if (vm.searchOpen) {
                item {
                    OutlinedTextField(
                        value = vm.query,
                        onValueChange = vm::search,
                        placeholder = { Text("جستجو در فروشنده، دسته یا متن پیامک") },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    )
                }
                if (vm.query.isNotBlank()) {
                    item {
                        val sumExp = vm.results.filter { it.type == TxType.EXPENSE }.sumOf { it.amount }
                        Text(
                            Fa.digits(vm.results.size.toString()) + " تراکنش  ·  " + show(sumExp) + " " + unit + " هزینه",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                        )
                    }
                    items(vm.results, key = { it.id }) { tx ->
                        TxRow(tx, show(tx.amount), unit, withDate = true) { selected = tx }
                    }
                }
            } else {
            item {
                val days = Jalali.daysIn(vm.month)
                val sums = LongArray(days + 1)
                modeList.forEach { t ->
                    val d = Jalali.dayOf(t.ts)
                    if (d in 1..days) sums[d] += t.amount
                }
                val now = System.currentTimeMillis()
                val elapsed = if (vm.month == Jalali.ymOf(now)) Jalali.dayOf(now).coerceAtLeast(1) else days
                val avg = modeTotal / elapsed
                val prevTotal = if (isExp) vm.prevExpense else vm.prevIncome
                val d = modeTotal - prevTotal
                var picked by remember(vm.month, vm.mode) { mutableStateOf<Int?>(null) }
                val barColor = if (isExp) Color(0xFFF59E0B) else Green

                Column(Modifier.padding(16.dp)) {
                    ModeToggle(vm.mode, vm::changeMode)
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 2.dp,
                    ) {
                        Column(
                            Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                (if (isExp) "خرج " else "دخل ") + Jalali.title(vm.month),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                show(modeTotal) + " " + unit,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                            )
                            val up = d >= 0
                            val good = if (isExp) !up else up
                            Text(
                                "اختلاف با ماه قبل: " + (if (up) "▲ " else "▼ ") + show(kotlin.math.abs(d)) + " " + unit,
                                fontSize = 12.sp,
                                color = if (prevTotal == 0L) MaterialTheme.colorScheme.onSurfaceVariant
                                else if (good) Green else Red,
                            )
                            Text(
                                "میانگین روزانه: " + show(avg) + " " + unit,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(14.dp))
                            val p = picked
                            Text(
                                if (p == null) "برای دیدن مبلغ هر روز، روی میله بزنید"
                                else Fa.digits("$p ${Jalali.monthNames[vm.month.month - 1]}") +
                                    ": " + show(sums[p]) + " " + unit,
                                fontSize = 12.sp,
                                fontWeight = if (p == null) FontWeight.Normal else FontWeight.Bold,
                                color = if (p == null) MaterialTheme.colorScheme.onSurfaceVariant else barColor,
                            )
                            Spacer(Modifier.height(6.dp))
                            DayBars(days, sums, barColor, picked) { picked = if (picked == it) null else it }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("۱", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    Fa.digits(days.toString()),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // ---- نمودار دسته‌ها ----
            if (byCat.isNotEmpty()) {
                item {
                    SectionTitle(if (isExp) "هزینه‌ها بر اساس دسته" else "درآمدها بر اساس دسته")
                }
                item {
                    Surface(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 2.dp,
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Donut(byCat, modeTotal, show(modeTotal), unit, if (isExp) "مجموع هزینه" else "مجموع درآمد")
                            Spacer(Modifier.height(18.dp))
                            byCat.forEachIndexed { i, (cat, sum) ->
                                if (i > 0) Spacer(Modifier.height(14.dp))
                                CategoryRow(cat, sum, modeTotal, show(sum), unit)
                            }
                        }
                    }
                }
            }

            // ---- تراکنش‌ها ----
            item { SectionTitle("تراکنش‌ها (" + Fa.digits(list.size.toString()) + ")") }

            if (list.isEmpty()) {
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

            byDay.forEach { (day, txs) ->
                item(key = "h_$day") {
                    Text(
                        day,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 6.dp),
                    )
                }
                items(txs, key = { it.id }) { tx ->
                    TxRow(tx, show(tx.amount), unit) { selected = tx }
                }
            }
            }
        }

        if (tab == 0) HomeTab(vm, { page = Page.SETTINGS }, { page = it }, { tab = it }) { selected = it }
        if (tab == 2) BudgetTab(vm)
        if (tab == 3) ObligationsTab(vm)

        BottomNav(tab, { tab = it }, Modifier.align(Alignment.BottomCenter))

        if (!vm.searchOpen && tab != 3) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 20.dp, bottom = 84.dp)
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Teal2, Teal1)))
                    .clickable { showAdd = true },
                contentAlignment = Alignment.Center,
            ) {
                Text("＋", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
        }

        when (page) {
            Page.SETTINGS -> SettingsScreen(vm, { page = null }, { page = it })
            Page.ACCOUNTS -> AccountsPage(vm, { page = Page.SETTINGS }, { page = Page.ADD_ACCOUNT })
            Page.ADD_ACCOUNT -> AddAccountPage(vm) { page = Page.ACCOUNTS }
            Page.PENDING -> PendingPage(vm) { page = null }
            Page.CATEGORIES -> CategoriesPage(vm) { page = Page.SETTINGS }
            Page.EXPORT -> ExportPage(vm) { page = Page.SETTINGS }
            Page.BACKUP -> BackupPage(vm) { page = Page.SETTINGS }
            null -> {}
        }

        SnackbarHost(
            snack,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (showAdd) AddCashDialog(vm) { showAdd = false }

    selected?.let { TxEditDialog(vm, it) { selected = null } }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 10.dp),
    )
}

@Composable
internal fun Pill(
    text: String,
    filled: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val bg = if (filled) Color.White else Color.White.copy(alpha = 0.18f)
    val fg = if (filled) Teal1 else Color.White
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (enabled) fg else fg.copy(alpha = 0.5f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
internal fun RoundBtn(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MiniStat(title: String, value: String, arrow: String, modifier: Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(arrow, color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
            Text(
                value,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun CatIcon(cat: String, size: Int) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Look.color(cat).copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(Look.icon(cat), fontSize = (size * 0.5f).sp)
    }
}

@Composable
private fun Donut(data: List<Pair<String, Long>>, total: Long, totalText: String, unit: String, label: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(190.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(180.dp)) {
            val stroke = 26.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            if (total <= 0L) return@Canvas
            var start = -90f
            data.forEach { (cat, sum) ->
                val sweep = 360f * sum.toFloat() / total.toFloat()
                val gap = if (data.size > 1) 2.5f else 0f
                drawArc(
                    color = Look.color(cat),
                    startAngle = start + gap / 2,
                    sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(totalText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(unit, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CategoryRow(cat: String, sum: Long, total: Long, sumText: String, unit: String) {
    val frac = if (total > 0) sum.toFloat() / total.toFloat() else 0f
    val color = Look.color(cat)
    Row(verticalAlignment = Alignment.CenterVertically) {
        CatIcon(cat, 40)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(cat, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(
                    "$sumText $unit",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(7.dp)
                        .clip(RoundedCornerShape(50))
                        .background(color.copy(alpha = 0.15f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(frac.coerceIn(0.02f, 1f))
                            .height(7.dp)
                            .clip(RoundedCornerShape(50))
                            .background(color)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    Fa.digits(Math.round(frac * 100).toString()) + "٪",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun TxRow(tx: Tx, amountText: String, unit: String, withDate: Boolean = false, onClick: () -> Unit) {
    val isIncome = tx.type == TxType.INCOME
    Surface(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CatIcon(tx.category, 44)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    titleOf(tx),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
                Text(
                    (if (tx.sender == "cash") "💵 نقدی · " else "") +
                        (tx.category + (if (tx.sub.isNotBlank()) " › " + tx.sub else "") + " · " +
                            (if (withDate) Jalali.full(tx.ts) else Jalali.time(tx.ts))),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    (if (isIncome) "+" else "−") + amountText,
                    color = if (isIncome) Green else Red,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
                Text(
                    unit,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun ModeToggle(mode: TxType, onChange: (TxType) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(TxType.EXPENSE to "خرج", TxType.INCOME to "دخل").forEach { (t, label) ->
            val sel = mode == t
            val bg = when {
                !sel -> MaterialTheme.colorScheme.surface
                t == TxType.EXPENSE -> Color(0xFFFFE8CC)
                else -> Color(0xFFD9F2E3)
            }
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(bg)
                    .clickable { onChange(t) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    color = if (sel) Color(0xFF1F2937) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** میله‌های روزانه؛ روز اول سمت راست است */
@Composable
private fun DayBars(days: Int, sums: LongArray, color: Color, picked: Int?, onPick: (Int) -> Unit) {
    val maxV = (sums.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(140.dp)
            .pointerInput(days) {
                detectTapGestures { off ->
                    val slot = size.width.toFloat() / days
                    val idx = (off.x / slot).toInt().coerceIn(0, days - 1)
                    onPick(days - idx)
                }
            }
    ) {
        val slot = size.width / days
        val barW = slot * 0.68f
        for (d in 1..days) {
            val v = sums[d]
            val h = if (v <= 0L) 3.dp.toPx() else (size.height * v.toFloat() / maxV.toFloat()).coerceAtLeast(6.dp.toPx())
            val x = (days - d) * slot + (slot - barW) / 2
            val sel = picked == d
            drawRoundRect(
                color = if (v <= 0L) color.copy(alpha = 0.15f) else if (sel) color else color.copy(alpha = 0.4f),
                topLeft = Offset(x, size.height - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            )
        }
    }
}
