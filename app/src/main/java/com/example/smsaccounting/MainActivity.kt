package com.example.smsaccounting

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = Color(0xFF6FD3B8))
    } else {
        lightColorScheme(primary = Color(0xFF1B6B5A))
    }
    MaterialTheme(colorScheme = colors, content = content)
}

private fun titleOf(tx: Tx): String =
    tx.merchant.ifBlank { tx.body.replace('\n', ' ').take(40) }

@OptIn(ExperimentalMaterial3Api::class)
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
    var menuOpen by remember { mutableStateOf(false) }

    val list = vm.items
    val income = list.filter { it.type == TxType.INCOME }.sumOf { it.amount }
    val expense = list.filter { it.type == TxType.EXPENSE }.sumOf { it.amount }
    val byCat = list.filter { it.type == TxType.EXPENSE }
        .groupBy { it.category }
        .map { (k, v) -> k to v.sumOf { it.amount } }
        .sortedByDescending { it.second }

    fun show(v: Long) = Fa.money(if (vm.toman) v / 10 else v)
    val unit = if (vm.toman) "تومان" else "ریال"
    val dark = isSystemInDarkTheme()
    val green = if (dark) Color(0xFF81C784) else Color(0xFF2E7D32)
    val red = if (dark) Color(0xFFE57373) else Color(0xFFC62828)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("حسابداری پیامک") },
                actions = {
                    TextButton(
                        onClick = { vm.importSms(false) },
                        enabled = hasPerm && !vm.busy,
                    ) { Text(if (vm.busy) "در حال خواندن…" else "همگام‌سازی") }
                    Box {
                        TextButton(onClick = { menuOpen = true }) { Text("⋮") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("بررسی مجدد همهٔ پیامک‌ها") },
                                onClick = {
                                    menuOpen = false
                                    if (hasPerm) vm.importSms(true)
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!hasPerm) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "برای خواندن پیامک‌های بانکی، اجازهٔ دسترسی به پیامک لازم است. " +
                                    "پیامک‌ها فقط روی همین گوشی پردازش می‌شوند و جایی ارسال نمی‌شوند."
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = {
                                launcher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_SMS,
                                        Manifest.permission.RECEIVE_SMS,
                                    )
                                )
                            }) { Text("اجازهٔ دسترسی") }
                        }
                    }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = vm::prevMonth) { Text("ماه قبل") }
                    Text(
                        Jalali.title(vm.month),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    TextButton(onClick = vm::nextMonth) { Text("ماه بعد") }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = vm.toman,
                        onClick = { vm.changeToman(true) },
                        label = { Text("تومان") },
                    )
                    FilterChip(
                        selected = !vm.toman,
                        onClick = { vm.changeToman(false) },
                        label = { Text("ریال") },
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("درآمد", show(income), unit, green, Modifier.weight(1f))
                    StatCard("هزینه", show(expense), unit, red, Modifier.weight(1f))
                }
            }
            item {
                val diff = income - expense
                StatCard(
                    "تراز این ماه",
                    (if (diff < 0) "−" else "") + show(kotlin.math.abs(diff)),
                    unit,
                    if (diff < 0) red else green,
                    Modifier.fillMaxWidth(),
                )
            }

            if (byCat.isNotEmpty()) {
                item {
                    Text(
                        "هزینه‌ها بر اساس دسته",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            byCat.forEach { (cat, sum) ->
                                val frac = if (expense > 0) sum.toFloat() / expense.toFloat() else 0f
                                Column {
                                    Row(Modifier.fillMaxWidth()) {
                                        Text(cat, modifier = Modifier.weight(1f))
                                        Text(
                                            "${show(sum)} $unit  ·  " +
                                                Fa.digits(Math.round(frac * 100).toString()) + "٪",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { frac },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "تراکنش‌ها (" + Fa.digits(list.size.toString()) + ")",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            if (list.isEmpty()) {
                item {
                    Text(
                        "در این ماه تراکنشی ثبت نشده است.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(list, key = { it.id }) { tx ->
                val isIncome = tx.type == TxType.INCOME
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable { selected = tx }
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                titleOf(tx),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                "${tx.category} · ${Jalali.dayTime(tx.ts)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            (if (isIncome) "+" else "−") + show(tx.amount),
                            color = if (isIncome) green else red,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }

    val tx = selected
    if (tx != null) {
        AlertDialog(
            onDismissRequest = { selected = null },
            title = {
                Text(if (tx.type == TxType.INCOME) "دسته‌بندی درآمد" else "دسته‌بندی هزینه")
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        tx.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    val cats = if (tx.type == TxType.INCOME) {
                        Categorizer.incomeCategories
                    } else {
                        Categorizer.expenseCategories
                    }
                    cats.forEach { c ->
                        TextButton(
                            onClick = {
                                vm.setCategory(tx, c)
                                selected = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = (if (c == tx.category) "✓ " else "") + c,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start,
                            )
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
                        Text("حذف", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
        )
    }
}

@Composable
private fun StatCard(title: String, value: String, unit: String, color: Color, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(
                unit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
