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

@Composable
fun ObligationsTab(vm: MainViewModel) {
    val ctx = LocalContext.current
    val unit = if (vm.toman) "تومان" else "ریال"
    fun show(v: Long) = Fa.money(if (vm.toman) v / 10 else v)
    var filter by remember { mutableStateOf<ObKind?>(null) }
    var dialog by remember { mutableStateOf<Obligation?>(null) }
    var creating by remember { mutableStateOf(false) }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val now = System.currentTimeMillis()
    val all = vm.obligations.filter { filter == null || it.kind == filter }
    val open = all.filter { !it.done }.sortedBy { it.dueTs }
    val done = all.filter { it.done }.sortedByDescending { it.dueTs }
    val needExact = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        !(ctx.getSystemService(android.app.AlarmManager::class.java)).canScheduleExactAlarms()

    Column(Modifier.fillMaxSize()) {
        TopBar("سررسیدها") {
            RoundBtn("＋") {
                if (Build.VERSION.SDK_INT >= 33 && !Notifs.canPost(ctx)) notif.launch(Manifest.permission.POST_NOTIFICATIONS)
                creating = true
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ChipRow {
                Chip("همه", filter == null) { filter = null }
                ObKind.values().forEach { k -> Chip(k.icon + " " + k.label, filter == k) { filter = k } }
            }
            if (needExact) {
                SectionCard {
                    Hint("برای اعلان سر ساعت دقیق، دسترسی «هشدار و یادآور» لازم است؛ بدون آن اعلان ممکن است کمی دیر برسد.")
                    Spacer(Modifier.height(8.dp))
                    SoftButton("باز کردن تنظیمات") {
                        try {
                            ctx.startActivity(
                                Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                    .setData(android.net.Uri.parse("package:" + ctx.packageName))
                            )
                        } catch (e: Exception) {
                        }
                    }
                }
            }
            if (open.isEmpty()) Hint("موردی ثبت نشده. با ＋ چک، طلب، بدهی، قسط یا وام اضافه کن.")
            open.forEach { o -> ObRow(o, now, ::show, unit) { dialog = o } }
            if (done.isNotEmpty()) {
                Text("انجام‌شده", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                done.forEach { o -> ObRow(o, now, ::show, unit) { dialog = o } }
            }
            Spacer(Modifier.height(140.dp))
        }
    }
    if (creating) ObligationDialog(vm, null) { creating = false }
    dialog?.let { ObligationDialog(vm, it) { dialog = null } }
}

@Composable
private fun ObRow(o: Obligation, now: Long, show: (Long) -> String, unit: String, onClick: () -> Unit) {
    val overdue = !o.done && o.dueTs < now
    Surface(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Text(o.kind.icon, fontSize = 20.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(o.kind.label + ": " + o.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    (if (o.party.isNotBlank()) o.party + " · " else "") + Jalali.dateTime(o.dueTs) +
                        (if (o.repeat) " · ماهانه" + (if (o.remaining > 0) " (" + Fa.digits(o.remaining.toString()) + " مانده)" else "") else ""),
                    fontSize = 11.sp,
                    color = if (overdue) Red else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (o.amount > 0) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        show(o.amount),
                        fontWeight = FontWeight.Bold,
                        color = if (o.kind == ObKind.CREDIT) Green else Red,
                    )
                    Text(unit, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun ObligationDialog(vm: MainViewModel, initial: Obligation?, onDismiss: () -> Unit) {
    val unit = if (vm.toman) "تومان" else "ریال"
    val key = initial?.id ?: -1L
    var kind by remember(key) { mutableStateOf(initial?.kind ?: ObKind.CHEQUE) }
    var title by remember(key) { mutableStateOf(initial?.title ?: "") }
    var party by remember(key) { mutableStateOf(initial?.party ?: "") }
    var amountText by remember(key) {
        mutableStateOf(initial?.let { if (it.amount == 0L) "" else (if (vm.toman) it.amount / 10 else it.amount).toString() } ?: "")
    }
    var due by remember(key) { mutableStateOf(initial?.dueTs ?: (Jalali.startOfDay(System.currentTimeMillis() + 86_400_000L) + 9 * 3_600_000L)) }
    var note by remember(key) { mutableStateOf(initial?.note ?: "") }
    var repeat by remember(key) { mutableStateOf(initial?.repeat ?: false) }
    var remainingText by remember(key) { mutableStateOf(initial?.remaining?.takeIf { it > 0 }?.toString() ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    val monthly = kind == ObKind.INSTALLMENT || kind == ObKind.LOAN

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(if (initial == null) "ثبت جدید" else "ویرایش", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ChipRow {
                    ObKind.values().forEach { k ->
                        Chip(k.icon + " " + k.label, kind == k) {
                            kind = k
                            if (k == ObKind.INSTALLMENT || k == ObKind.LOAN) repeat = true else repeat = false
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Field(title, { title = it }, if (kind == ObKind.CHEQUE) "شمارهٔ چک / عنوان" else "عنوان")
                Spacer(Modifier.height(8.dp))
                Field(
                    party, { party = it },
                    when (kind) {
                        ObKind.CHEQUE -> "در وجه / صادرکننده"
                        ObKind.CREDIT -> "بدهکار (کی باید بدهد)"
                        ObKind.DEBT -> "طلبکار (به کی بدهکارم)"
                        else -> "بانک / مؤسسه"
                    },
                )
                Spacer(Modifier.height(8.dp))
                Field(amountText, { amountText = it }, "مبلغ ($unit)", number = true)
                Spacer(Modifier.height(10.dp))
                DateTimeRow("زمان سررسید و اعلان", due, withTime = true) { due = it }
                if (monthly) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("تکرار ماهانه", modifier = Modifier.weight(1f))
                        Switch(checked = repeat, onCheckedChange = { repeat = it })
                    }
                    if (repeat) {
                        Field(remainingText, { remainingText = it }, "تعداد اقساط باقی‌مانده (خالی = نامحدود)", number = true)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Field(note, { note = it }, "توضیحات")
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = {
                val a = parseAmount(amountText)
                vm.saveObligation(
                    Obligation(
                        id = initial?.id ?: 0L,
                        kind = kind,
                        title = title.trim(),
                        party = party.trim(),
                        amount = if (vm.toman) a * 10 else a,
                        dueTs = due,
                        note = note.trim(),
                        done = initial?.done ?: false,
                        repeat = monthly && repeat,
                        remaining = if (monthly && repeat) parseAmount(remainingText).toInt().coerceAtLeast(0) else 0,
                        created = initial?.created ?: System.currentTimeMillis(),
                    )
                )
                onDismiss()
            }) { Text("ذخیره") }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    if (initial.done) {
                        TextButton(onClick = {
                            vm.reopenObligation(initial)
                            onDismiss()
                        }) { Text("بازگردانی") }
                    } else {
                        TextButton(onClick = {
                            vm.finishObligation(initial)
                            onDismiss()
                        }) { Text("انجام شد", color = Green) }
                    }
                    TextButton(onClick = { confirmDelete = true }) { Text("حذف", color = Red) }
                }
                TextButton(onClick = onDismiss) { Text("انصراف") }
            }
        },
    )
    if (confirmDelete && initial != null) {
        ConfirmDialog("حذف", "این مورد حذف شود؟", onDismiss = { confirmDelete = false }) {
            vm.deleteObligation(initial)
            onDismiss()
        }
    }
}
