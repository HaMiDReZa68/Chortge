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

/** انتخاب سرشمارهٔ پیامک از میان پیامک‌های گوشی (یا نوشتن دستی) + عبارت شناسایی اختیاری */
@Composable
fun SenderPicker(vm: MainViewModel, sender: String, onSender: (String) -> Unit, match: String, onMatch: (String) -> Unit) {
    val (ok, ask) = rememberSmsPermission()
    Text("پیامک بانک را انتخاب کن", fontWeight = FontWeight.Bold, fontSize = 14.sp)
    Spacer(Modifier.height(4.dp))
    Hint("فقط پیامک‌هایی که شبیه تراکنش بانکی هستند نشان داده می‌شوند. از این سرشماره به بعد، هر پیامک تازه برایت اعلان می‌آورد.")
    Spacer(Modifier.height(10.dp))
    if (!ok) {
        PrimaryButton("اجازهٔ دسترسی به پیامک", onClick = ask)
    } else {
        SoftButton(if (vm.scanning) "در حال خواندن…" else "خواندن پیامک‌های بانکی گوشی", enabled = !vm.scanning) {
            vm.scanSenders()
        }
    }
    Spacer(Modifier.height(8.dp))
    vm.candidates.forEach { c ->
        val sel = Norm.sender(c.sender) == Norm.sender(sender)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (sel) Teal1.copy(alpha = 0.14f) else MaterialTheme.colorScheme.background)
                .clickable { onSender(c.sender) }
                .padding(12.dp)
        ) {
            Row {
                Text(c.sender, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(Fa.digits(c.count.toString()) + " پیامک", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                c.sample.replace('\n', ' '),
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (ok && !vm.scanning && vm.candidates.isEmpty()) Hint("اگر چیزی نشان داده نشد، سرشماره را دستی بنویس.")
    Spacer(Modifier.height(8.dp))
    Field(sender, onSender, "سرشمارهٔ پیامک (مثلاً 9830...)")
    Spacer(Modifier.height(8.dp))
    Field(match, onMatch, "عبارت شناسایی حساب (اختیاری، مثلاً ۴ رقم آخر کارت)")
    Spacer(Modifier.height(4.dp))
    Hint("اگر چند حساب از یک سرشماره پیامک می‌گیرند، این عبارت مشخص می‌کند پیامک مال کدام حساب است.")
}

@Composable
fun AddAccountPage(vm: MainViewModel, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var bank by remember { mutableStateOf("") }
    var sender by remember { mutableStateOf("") }
    var match by remember { mutableStateOf("") }
    var balText by remember { mutableStateOf("") }
    var readOld by remember { mutableStateOf(true) }
    var since by remember { mutableStateOf(Jalali.startOfDay(System.currentTimeMillis() - 30L * 86_400_000L)) }
    LaunchedEffect(Unit) { vm.scanSenders() }
    LaunchedEffect(sender) {
        if (bank.isBlank() && sender.isNotBlank()) {
            val g = BankNames.guess(sender)
            if (g != sender) bank = g
        }
    }
    PageScaffold("افزودن حساب بانکی", onBack) {
        SectionCard {
            Field(name, { name = it }, "نام حساب (مثلاً حقوق، جاری)")
            Spacer(Modifier.height(10.dp))
            Field(bank, { bank = it }, "نام بانک")
            Spacer(Modifier.height(10.dp))
            Field(balText, { balText = it }, "مانده فعلی حساب (${if (vm.toman) "تومان" else "ریال"})", number = true)
            Hint("با هر پیامک بعدی، مانده از متن پیامک به‌روز می‌شود.")
        }
        SectionCard { SenderPicker(vm, sender, { sender = it }, match, { match = it }) }
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("خواندن پیامک‌های قبلی از یک تاریخ", modifier = Modifier.weight(1f))
                Switch(checked = readOld, onCheckedChange = { readOld = it })
            }
            if (readOld) {
                Spacer(Modifier.height(10.dp))
                DateTimeRow("از تاریخ", since, withTime = false) { since = Jalali.startOfDay(it) }
            }
        }
        PrimaryButton(
            if (vm.busy) "در حال کار…" else "ایجاد حساب",
            enabled = name.isNotBlank() && sender.isNotBlank() && !vm.busy,
        ) {
            vm.createAccount(name, bank.ifBlank { BankNames.guess(sender) }, sender, match, if (readOld) since else null,
                if (balText.isBlank()) null else parseAmount(balText).let { if (vm.toman) it * 10 else it })
            onBack()
        }
    }
}

@Composable
fun AccountsPage(vm: MainViewModel, onBack: () -> Unit, onAdd: () -> Unit) {
    val unit = if (vm.toman) "تومان" else "ریال"
    fun show(v: Long) = Fa.money(if (vm.toman) v / 10 else v)
    var edit by remember { mutableStateOf<Account?>(null) }
    var delete by remember { mutableStateOf<Account?>(null) }
    var addSrc by remember { mutableStateOf<Account?>(null) }
    var importFor by remember { mutableStateOf<Account?>(null) }

    PageScaffold("حساب‌ها و سرشماره‌ها", onBack, action = { RoundBtn("＋", onAdd) }) {
        vm.accounts.forEach { a ->
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (a.isCash) "💵" else "🏦", fontSize = 24.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.name, fontWeight = FontWeight.Bold)
                        if (!a.isCash) Hint(a.bank)
                    }
                    if (!a.isCash) {
                        TextButton(onClick = { edit = a }) { Text("✎") }
                        TextButton(onClick = { delete = a }) { Text("🗑", color = Red) }
                    }
                }
                if (a.isCash) {
                    Hint("با دکمهٔ ＋ در صفحهٔ اصلی ثبت می‌شود.")
                } else {
                    val bal = a.lastBalance
                    Spacer(Modifier.height(6.dp))
                    Hint(if (bal != null) "آخرین مانده: " + show(bal) + " " + unit else "مانده هنوز مشخص نیست")
                    Spacer(Modifier.height(10.dp))
                    Text("سرشماره‌ها", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    val srcs = vm.sources.filter { it.accountId == a.id }
                    if (srcs.isEmpty()) Hint("سرشماره‌ای وصل نیست؛ پیامکی دریافت نمی‌شود.")
                    srcs.forEach { s ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(s.sender, fontSize = 14.sp)
                                if (s.match.isNotBlank()) Hint("شامل: " + s.match)
                            }
                            TextButton(onClick = { vm.removeSource(s.id) }) { Text("حذف", color = Red) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SoftButton("＋ سرشماره", Modifier.weight(1f)) { addSrc = a }
                        SoftButton("خواندن از تاریخ…", Modifier.weight(1f), enabled = srcs.isNotEmpty() && !vm.busy) { importFor = a }
                    }
                }
            }
        }
        Hint("پیامک بانک‌هایی که اینجا ثبت نشده باشند هرگز خوانده نمی‌شوند.")
    }

    edit?.let { a ->
        var n by remember(a.id) { mutableStateOf(a.name) }
        var b by remember(a.id) { mutableStateOf(a.bank) }
        var bal by remember(a.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { edit = null },
            shape = RoundedCornerShape(24.dp),
            title = { Text("ویرایش حساب", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Field(n, { n = it }, "نام حساب")
                    Spacer(Modifier.height(8.dp))
                    Field(b, { b = it }, "نام بانک")
                    Spacer(Modifier.height(8.dp))
                    Field(bal, { bal = it }, "اصلاح مانده (${if (vm.toman) "تومان" else "ریال"}، اختیاری)", number = true)
                }
            },
            confirmButton = {
                TextButton(enabled = n.isNotBlank(), onClick = {
                    vm.renameAccount(a.id, n, b, if (bal.isBlank()) null else parseAmount(bal).let { if (vm.toman) it * 10 else it })
                    edit = null
                }) { Text("ذخیره") }
            },
            dismissButton = { TextButton(onClick = { edit = null }) { Text("انصراف") } },
        )
    }
    delete?.let { a ->
        ConfirmDialog("حذف حساب", "حساب «${a.name}» و تراکنش‌هایش حذف شود؟", onDismiss = { delete = null }) {
            vm.deleteAccount(a.id)
        }
    }
    addSrc?.let { a ->
        var sender by remember(a.id) { mutableStateOf("") }
        var match by remember(a.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addSrc = null },
            shape = RoundedCornerShape(24.dp),
            title = { Text("سرشمارهٔ جدید برای «${a.name}»", fontWeight = FontWeight.Bold) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { SenderPicker(vm, sender, { sender = it }, match, { match = it }) } },
            confirmButton = {
                TextButton(enabled = sender.isNotBlank(), onClick = {
                    vm.addSource(sender, a.id, match)
                    addSrc = null
                }) { Text("افزودن") }
            },
            dismissButton = { TextButton(onClick = { addSrc = null }) { Text("انصراف") } },
        )
    }
    importFor?.let { a ->
        JalaliDateDialog(System.currentTimeMillis() - 30L * 86_400_000L, { importFor = null }) { d ->
            vm.importAccount(a.id, Jalali.startOfDay(d))
        }
    }
}

@Composable
fun PendingPage(vm: MainViewModel, onBack: () -> Unit) {
    val unit = if (vm.toman) "تومان" else "ریال"
    var reg by remember { mutableStateOf<PendingTx?>(null) }
    PageScaffold("تراکنش‌های منتظر ثبت", onBack) {
        if (vm.pendings.isEmpty()) Hint("همهٔ تراکنش‌ها ثبت شده‌اند.")
        vm.pendings.forEach { p ->
            val acc = vm.accounts.firstOrNull { it.id == p.accountId }
            SectionCard(Modifier.clickable { reg = p }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            (if (p.type == TxType.EXPENSE) "برداشت " else "واریز ") + Fa.money(if (vm.toman) p.amount / 10 else p.amount) + " " + unit,
                            fontWeight = FontWeight.Bold,
                            color = if (p.type == TxType.EXPENSE) Red else Green,
                        )
                        Hint((acc?.name ?: "") + " · " + Jalali.dateTime(p.ts))
                    }
                    Text("ثبت ›", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                Text(p.body.replace('\n', ' '), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    reg?.let { RegisterDialog(vm, it) { reg = null } }
}

@Composable
fun RegisterDialog(vm: MainViewModel, p: PendingTx, onDismiss: () -> Unit) {
    var type by remember(p.id) { mutableStateOf(p.type) }
    var cat by remember(p.id) { mutableStateOf(p.category) }
    var sub by remember(p.id) { mutableStateOf(p.sub) }
    var note by remember(p.id) { mutableStateOf("") }
    val unit = if (vm.toman) "تومان" else "ریال"
    var amountText by remember(p.id) { mutableStateOf((if (vm.toman) p.amount / 10 else p.amount).toString()) }
    val amount = parseAmount(amountText)
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("ثبت تراکنش", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(p.body, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 6, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                ModeToggle(type) {
                    type = it
                    cat = vm.cats.firstOrNull { c -> c.type == it && c.parentId == 0L }?.name ?: cat
                    sub = ""
                }
                Spacer(Modifier.height(10.dp))
                Field(amountText, { amountText = it }, "مبلغ ($unit)", number = true)
                Spacer(Modifier.height(10.dp))
                CategoryPicker(vm.cats, type, cat, sub) { c, s ->
                    cat = c
                    sub = s
                }
                Spacer(Modifier.height(10.dp))
                Field(note, { note = it }, "توضیحات")
            }
        },
        confirmButton = {
            TextButton(enabled = amount > 0, onClick = {
                vm.registerPending(p, type, if (vm.toman) amount * 10 else amount, cat, sub, note)
                onDismiss()
            }) { Text("ثبت") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    vm.dismissPending(p)
                    onDismiss()
                }) { Text("نادیده گرفتن", color = Red) }
                TextButton(onClick = onDismiss) { Text("بعداً") }
            }
        },
    )
}
