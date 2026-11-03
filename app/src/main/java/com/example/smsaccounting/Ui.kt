package com.example.smsaccounting

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class Page { SETTINGS, ACCOUNTS, ADD_ACCOUNT, PENDING, CATEGORIES, EXPORT, BACKUP }

/** صفحهٔ تمام‌صفحه با نوار بالای گرادیانی */
@Composable
fun PageScaffold(
    title: String,
    onBack: () -> Unit,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Teal1, Teal2)))
                .statusBarsPadding()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundBtn("›", onBack)
            Spacer(Modifier.width(12.dp))
            Text(
                title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.weight(1f),
            )
            action?.invoke()
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Teal1),
        modifier = modifier.fillMaxWidth(),
    ) { Text(text, color = Color.White, fontWeight = FontWeight.Bold) }
}

@Composable
fun SoftButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Teal1.copy(alpha = if (enabled) 0.12f else 0.05f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun Hint(text: String) {
    Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    number: Boolean = false,
    singleLine: Boolean = true,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
    )
}

fun parseAmount(s: String): Long = Norm.normalize(s).replace(",", "").replace("٬", "").toLongOrNull() ?: 0L

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
fun Chip(text: String, selected: Boolean, color: Color = Teal1, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) color.copy(alpha = 0.22f) else MaterialTheme.colorScheme.background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String = "حذف", onDismiss: () -> Unit, onOk: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(text, fontSize = 14.sp) },
        confirmButton = {
            TextButton(onClick = {
                onOk()
                onDismiss()
            }) { Text(confirm, color = Red) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

// ---------------------------------------------------------------- تاریخ و ساعت شمسی

@Composable
private fun Stepper(label: String, onUp: () -> Unit, onDown: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TextButton(onClick = onUp) { Text("▲") }
        Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        TextButton(onClick = onDown) { Text("▼") }
    }
}

@Composable
fun JalaliDateDialog(initial: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val p = remember { Jalali.parts(initial) }
    var y by remember { mutableStateOf(p[0]) }
    var m by remember { mutableStateOf(p[1]) }
    var d by remember { mutableStateOf(p[2]) }
    val maxD = Jalali.daysIn(Jalali.YM(y, m))
    val day = d.coerceAtMost(maxD)
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("انتخاب تاریخ", fontWeight = FontWeight.Bold) },
        text = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stepper(Fa.digits(day.toString()), { d = if (day >= maxD) 1 else day + 1 }, { d = if (day <= 1) maxD else day - 1 })
                Stepper(Jalali.monthNames[m - 1], { m = if (m == 12) 1 else m + 1 }, { m = if (m == 1) 12 else m - 1 })
                Stepper(Fa.digits(y.toString()), { y = (y + 1).coerceAtMost(1500) }, { y = (y - 1).coerceAtLeast(1350) })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onPick(Jalali.toMillis(y, m, day))
                onDismiss()
            }) { Text("تأیید") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

/** دکمهٔ تاریخ (و اختیاری ساعت) که مقدار را به میلی‌ثانیه برمی‌گرداند */
@Composable
fun DateTimeRow(label: String, ts: Long, withTime: Boolean, onChange: (Long) -> Unit) {
    val ctx = LocalContext.current
    var dlg by remember { mutableStateOf(false) }
    Column {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SoftButton("📅 " + Jalali.full(ts), Modifier.weight(1f)) { dlg = true }
            if (withTime) {
                val p = Jalali.parts(ts)
                SoftButton("🕒 " + Jalali.time(ts), Modifier.weight(0.6f)) {
                    TimePickerDialog(
                        ctx,
                        { _, h, mi -> onChange(Jalali.toMillis(p[0], p[1], p[2], h, mi)) },
                        p[3], p[4], true,
                    ).show()
                }
            }
        }
    }
    if (dlg) {
        JalaliDateDialog(ts, { dlg = false }) { picked ->
            val p = Jalali.parts(ts)
            val q = Jalali.parts(picked)
            onChange(Jalali.toMillis(q[0], q[1], q[2], p[3], p[4]))
        }
    }
}

// ---------------------------------------------------------------- دسته‌ها

/** انتخاب دسته و زیرشاخه از فهرست دسته‌های کاربر */
@Composable
fun CategoryPicker(
    cats: List<Category>,
    type: TxType,
    category: String,
    sub: String,
    onChange: (String, String) -> Unit,
) {
    val tops = cats.filter { it.type == type && it.parentId == 0L }
    val cur = tops.firstOrNull { it.name == category }
    val subs = if (cur == null) emptyList() else cats.filter { it.parentId == cur.id }
    Text("بابت", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    ChipRow {
        tops.forEach { c ->
            Chip(Look.icon(c.name) + " " + c.name, c.name == category, Look.color(c.name)) { onChange(c.name, "") }
        }
    }
    if (subs.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Text("زیرشاخه", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        ChipRow {
            Chip("بدون زیرشاخه", sub.isEmpty()) { onChange(category, "") }
            subs.forEach { s -> Chip(s.name, s.name == sub) { onChange(category, s.name) } }
        }
    }
}

// ---------------------------------------------------------------- ویرایش تراکنش

@Composable
fun TxEditDialog(vm: MainViewModel, tx: Tx, onDismiss: () -> Unit) {
    var type by remember(tx.id) { mutableStateOf(tx.type) }
    var cat by remember(tx.id) { mutableStateOf(tx.category) }
    var sub by remember(tx.id) { mutableStateOf(tx.sub) }
    var note by remember(tx.id) { mutableStateOf(tx.note) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("ویرایش تراکنش", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Hint(Jalali.dateTime(tx.ts))
                Spacer(Modifier.height(4.dp))
                Text(
                    tx.body,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                if (tx.type.isTransfer) {
                    Text("🔁 انتقال بین حساب‌ها · " + tx.sub, fontWeight = FontWeight.Bold)
                } else {
                    ModeToggle(type) {
                        type = it
                        cat = vm.cats.firstOrNull { c -> c.type == it && c.parentId == 0L }?.name ?: cat
                        sub = ""
                    }
                    Spacer(Modifier.height(12.dp))
                    CategoryPicker(vm.cats, type, cat, sub) { c, s ->
                        cat = c
                        sub = s
                    }
                }
                Spacer(Modifier.height(12.dp))
                Field(note, { note = it }, "توضیحات")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.saveTx(tx, type, cat, sub, note)
                onDismiss()
            }) { Text("ذخیره") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { confirmDelete = true }) { Text("حذف", color = Red) }
                TextButton(onClick = onDismiss) { Text("انصراف") }
            }
        },
    )
    if (confirmDelete) {
        ConfirmDialog("حذف تراکنش", "این تراکنش حذف شود؟", onDismiss = { confirmDelete = false }) {
            vm.remove(tx)
            onDismiss()
        }
    }
}

/** ثبت تراکنش نقدی */
@Composable
fun AddCashDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    var type by remember { mutableStateOf(TxType.EXPENSE) }
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf<String?>(null) }
    var sub by remember { mutableStateOf("") }
    var accId by remember { mutableStateOf(1L) }
    var transfer by remember { mutableStateOf(false) }
    var toId by remember { mutableStateOf(0L) }
    var ts by remember { mutableStateOf(System.currentTimeMillis()) }
    val first = vm.cats.firstOrNull { it.type == type && it.parentId == 0L }?.name ?: ""
    val auto = remember(type, note) { if (note.isBlank()) null else vm.suggest(type, note) }
    val effCat = cat ?: auto?.category ?: first
    val effSub = if (cat != null) sub else auto?.sub ?: ""
    val amount = parseAmount(amountText)
    val unit = if (vm.toman) "تومان" else "ریال"

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("ثبت تراکنش دستی", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ModeToggle(type) {
                    type = it
                    cat = null
                    sub = ""
                    transfer = false
                }
                Spacer(Modifier.height(8.dp))
                Chip("🔁 انتقال بین حساب‌های خودم", transfer) { transfer = !transfer }
                Spacer(Modifier.height(10.dp))
                Text(
                    if (transfer) "از کدام حساب؟" else if (type == TxType.EXPENSE) "از کدام حساب؟" else "به کدام حساب واریز شد؟",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                ChipRow {
                    vm.accounts.forEach { a ->
                        Chip((if (a.isCash) "💵 " else "🏦 ") + a.name, accId == a.id) { accId = a.id }
                    }
                }
                if (transfer) {
                    Spacer(Modifier.height(10.dp))
                    Text("به کدام حساب؟", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    ChipRow {
                        vm.accounts.filter { it.id != accId }.forEach { a ->
                            Chip((if (a.isCash) "💵 " else "🏦 ") + a.name, toId == a.id) { toId = a.id }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Field(amountText, { amountText = it }, "مبلغ ($unit)", number = true)
                Spacer(Modifier.height(10.dp))
                DateTimeRow("تاریخ و ساعت", ts, withTime = true) { ts = it }
                Spacer(Modifier.height(10.dp))
                Field(note, { note = it }, "توضیحات (مثلاً نانوایی)")
                if (!transfer) {
                    Spacer(Modifier.height(12.dp))
                    CategoryPicker(vm.cats, type, effCat, effSub) { c, s ->
                        cat = c
                        sub = s
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = amount > 0 && (if (transfer) toId != 0L && toId != accId else effCat.isNotEmpty()),
                onClick = {
                    if (transfer) {
                        vm.addTransfer(accId, toId, if (vm.toman) amount * 10 else amount, note, ts)
                        onDismiss()
                        return@TextButton
                    }
                    vm.addManual(type, if (vm.toman) amount * 10 else amount, note, effCat, effSub, accId, ts)
                    onDismiss()
                },
            ) { Text("ثبت") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

// ---------------------------------------------------------------- دسترسی پیامک

/** (دسترسی داده شده؟، درخواست دسترسی) */
@Composable
fun rememberSmsPermission(): Pair<Boolean, () -> Unit> {
    val ctx = LocalContext.current
    fun granted() = ctx.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED &&
        ctx.checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    var ok by remember { mutableStateOf(granted()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        ok = granted()
    }
    return ok to {
        val list = ArrayList<String>()
        list.add(Manifest.permission.READ_SMS)
        list.add(Manifest.permission.RECEIVE_SMS)
        if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
        launcher.launch(list.toTypedArray())
    }
}
