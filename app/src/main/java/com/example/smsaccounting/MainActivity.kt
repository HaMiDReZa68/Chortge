package com.example.smsaccounting

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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

private val Teal1 = Color(0xFF0F766E)
private val Teal2 = Color(0xFF14B8A6)
private val Green = Color(0xFF16A34A)
private val Red = Color(0xFFE11D48)

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            AppTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Screen(vm)
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

private fun titleOf(tx: Tx): String =
    tx.merchant.ifBlank { tx.body.replace('\n', ' ').take(40) }

@Composable
fun Screen(vm: MainViewModel) {
    val ctx = LocalContext.current
    fun granted() =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

    var hasPerm by remember { mutableStateOf(granted()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { hasPerm = granted() }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { hasPerm = granted() }

    LaunchedEffect(hasPerm) { if (hasPerm) vm.importSms(false) }

    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.status) {
        val s = vm.status
        if (s != null) {
            snack.showSnackbar(s)
            vm.clearStatus()
        }
    }

    var selected by remember { mutableStateOf<Tx?>(null) }

    val list = vm.items
    val income = list.filter { it.type == TxType.INCOME }.sumOf { it.amount }
    val expense = list.filter { it.type == TxType.EXPENSE }.sumOf { it.amount }
    val byCat = list.filter { it.type == TxType.EXPENSE }
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
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
                            fontSize = 20.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Pill(
                            text = if (vm.busy) "در حال خواندن…" else "همگام‌سازی",
                            filled = false,
                            enabled = hasPerm && !vm.busy,
                            onClick = { vm.importSms(false) },
                        )
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

            // ---- درخواست دسترسی ----
            if (!hasPerm) {
                item {
                    Card(
                        modifier = Modifier.padding(16.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4D6)),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "برای خواندن پیامک‌های بانکی، اجازهٔ دسترسی به پیامک لازم است. " +
                                    "پیامک‌ها فقط روی همین گوشی پردازش می‌شوند و جایی ارسال نمی‌شوند.",
                                color = Color(0xFF5B4A00),
                                fontSize = 13.sp,
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    launcher.launch(
                                        arrayOf(
                                            Manifest.permission.READ_SMS,
                                            Manifest.permission.RECEIVE_SMS,
                                        )
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Teal1),
                            ) { Text("اجازهٔ دسترسی", color = Color.White) }
                        }
                    }
                }
            }

            // ---- نمودار دسته‌ها ----
            if (byCat.isNotEmpty()) {
                item {
                    SectionTitle("هزینه‌ها بر اساس دسته")
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
                            Donut(byCat, expense, show(expense), unit)
                            Spacer(Modifier.height(18.dp))
                            byCat.forEachIndexed { i, (cat, sum) ->
                                if (i > 0) Spacer(Modifier.height(14.dp))
                                CategoryRow(cat, sum, expense, show(sum), unit)
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

        SnackbarHost(
            snack,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    val tx = selected
    if (tx != null) {
        AlertDialog(
            onDismissRequest = { selected = null },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    if (tx.type == TxType.INCOME) "دسته‌بندی درآمد" else "دسته‌بندی هزینه",
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        tx.body,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(10.dp))
                    val cats = if (tx.type == TxType.INCOME) {
                        Categorizer.incomeCategories
                    } else {
                        Categorizer.expenseCategories
                    }
                    cats.forEach { c ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (c == tx.category) Look.color(c).copy(alpha = 0.15f) else Color.Transparent
                                )
                                .clickable {
                                    vm.setCategory(tx, c)
                                    selected = null
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CatIcon(c, 34)
                            Spacer(Modifier.width(12.dp))
                            Text(c, modifier = Modifier.weight(1f))
                            if (c == tx.category) Text("✓", color = Look.color(c), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selected = null }) { Text("بستن") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        vm.toggleType(tx)
                        selected = null
                    }) {
                        Text(if (tx.type == TxType.INCOME) "این هزینه است" else "این درآمد است")
                    }
                    TextButton(onClick = {
                        vm.remove(tx)
                        selected = null
                    }) {
                        Text("حذف", color = Red)
                    }
                }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 10.dp),
    )
}

@Composable
private fun Pill(
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
private fun RoundBtn(label: String, onClick: () -> Unit) {
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
private fun CatIcon(cat: String, size: Int) {
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
private fun Donut(data: List<Pair<String, Long>>, total: Long, totalText: String, unit: String) {
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
                "مجموع هزینه",
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
private fun TxRow(tx: Tx, amountText: String, unit: String, onClick: () -> Unit) {
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
                    "${tx.category} · ${Jalali.time(tx.ts)}",
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
