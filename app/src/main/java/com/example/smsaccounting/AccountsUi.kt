package com.example.smsaccounting

import androidx.compose.runtime.saveable.rememberSaveable
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
                .background(if (sel) Teal1.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant)
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
    var name by rememberSaveable { mutableStateOf("") }
    var bank by rememberSaveable { mutableStateOf("") }
    var sender by rememberSaveable { mutableStateOf("") }
    var match by rememberSaveable { mutableStateOf("") }
    var balText by rememberSaveable { mutableStateOf("") }
    var readOld by rememberSaveable { mutableStateOf(true) }
    var since by rememberSaveable { mutableStateOf(Jalali.startOfDay(System.currentTimeMillis() - 30L * 86_400_000L)) }
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
            Field(
                balText, { balText = it },
                (if (readOld) "مانده حساب در تاریخ شروع" else "مانده فعلی حساب") + " (${if (vm.toman) "تومان" else "ریال"})",
                number = true,
            )
            Hint("موجودی برنامه = این مبلغ + تراکنش‌هایی که ثبت می‌کنی. موجودی داخل پیامک ذخیره نمی‌شود و فقط برای مقایسه نشان داده می‌شود.")
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
                    Hint("موجودی (طبق تراکنش‌های ثبت‌شده): " + show(bal ?: 0L) + " " + unit)
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
        var n by rememberSaveable(a.id) { mutableStateOf(a.name) }
        var b by rememberSaveable(a.id) { mutableStateOf(a.bank) }
        var bal by rememberSaveable(a.id) { mutableStateOf("") }
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
                    Field(bal, { bal = it }, "اصلاح موجودی فعلی (${if (vm.toman) "تومان" else "ریال"}، اختیاری)", number = true, money = true)
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
        var sender by rememberSaveable(a.id) { mutableStateOf("") }
        var match by rememberSaveable(a.id) { mutableStateOf("") }
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
fun PendingTab(vm: MainViewModel) {
    val unit = if (vm.toman) "تومان" else "ریال"
    fun show(v: Long) = Fa.money(if (vm.toman) v / 10 else v)
    var regId by rememberSaveable { mutableStateOf(0L) }
    var confirmAll by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TopBar("دسته‌بندی‌نشده") {
            if (vm.pendings.isNotEmpty()) {
                Text(
                    Fa.digits(vm.pendings.size.toString()) + " مورد",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
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
            if (vm.pendings.isEmpty()) {
                Hint("پیامک‌های تازهٔ بانک‌هایی که اضافه کرده‌ای و پیامک‌های خوانده‌شده از تاریخ مشخص اینجا می‌آیند تا بابت و توضیحاتشان را بزنی.")
            } else {
                SoftButton("ثبت همه با دستهٔ پیشنهادی", Modifier.fillMaxWidth()) { confirmAll = true }
            }
            vm.pendings.forEach { p ->
                val acc = vm.accounts.firstOrNull { it.id == p.accountId }
                SectionCard(Modifier.clickable { regId = p.id }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                (if (p.type == TxType.EXPENSE) "برداشت " else "واریز ") + show(p.amount) + " " + unit,
                                fontWeight = FontWeight.Bold,
                                color = if (p.type == TxType.EXPENSE) Red else Green,
                            )
                            Hint((acc?.name ?: "") + " · " + Jalali.dateTime(p.ts))
                            Hint("پیشنهاد: " + Look.icon(p.category) + " " + p.category + if (p.sub.isNotBlank()) " › " + p.sub else "")
                        }
                        Text("ثبت ›", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(p.body.replace('\n', ' '), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(140.dp))
        }
    }
    vm.pendings.firstOrNull { it.id == regId }?.let { RegisterDialog(vm, it) { regId = 0L } }
    if (confirmAll) {
        ConfirmDialog(
            "ثبت همه",
            "همهٔ تراکنش‌ها با دستهٔ پیشنهادی ثبت شوند؟",
            confirm = "ثبت",
            onDismiss = { confirmAll = false },
        ) { vm.registerAllPending() }
    }
}

@Composable
fun RegisterDialog(vm: MainViewModel, p: PendingTx, onDismiss: () -> Unit) {
    var type by rememberSaveable(p.id) { mutableStateOf(p.type) }
    var cat by rememberSaveable(p.id) { mutableStateOf(p.category) }
    var sub by rememberSaveable(p.id) { mutableStateOf(p.sub) }
    var note by rememberSaveable(p.id) { mutableStateOf("") }
    var transfer by rememberSaveable(p.id) { mutableStateOf(false) }
    var otherId by rememberSaveable(p.id) { mutableStateOf(0L) }
    val unit = if (vm.toman) "تومان" else "ریال"
    var amountText by rememberSaveable(p.id) { mutableStateOf((if (vm.toman) p.amount / 10 else p.amount).toString()) }
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
                Spacer(Modifier.height(8.dp))
                Chip("🔁 انتقال بین حساب‌های خودم", transfer) { transfer = !transfer }
                Spacer(Modifier.height(10.dp))
                Field(amountText, { amountText = it }, "مبلغ ($unit)", number = true, money = true)
                Spacer(Modifier.height(10.dp))
                if (transfer) {
                    Text(
                        if (p.type == TxType.EXPENSE) "به کدام حساب منتقل شد؟" else "از کدام حساب آمد؟",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    ChipRow {
                        vm.accounts.filter { it.id != p.accountId }.forEach { a ->
                            Chip((if (a.isCash) "💵 " else "🏦 ") + a.name, otherId == a.id) { otherId = a.id }
                        }
                    }
                } else {
                    CategoryPicker(vm.cats, type, cat, sub) { c, s ->
                        cat = c
                        sub = s
                    }
                }
                Spacer(Modifier.height(10.dp))
                Field(note, { note = it }, "توضیحات")
                val smsBal = p.balance
                val appBal = vm.accounts.firstOrNull { it.id == p.accountId }?.lastBalance
                if (smsBal != null && appBal != null) {
                    val rial = if (vm.toman) amount * 10 else amount
                    val inc = if (transfer) p.type == TxType.INCOME else type == TxType.INCOME
                    val expected = appBal + if (inc) rial else -rial
                    val diff = smsBal - expected
                    fun m(v: Long) = Fa.money(if (vm.toman) v / 10 else v) + " " + unit
                    Spacer(Modifier.height(12.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (diff == 0L) Green.copy(alpha = 0.12f) else Red.copy(alpha = 0.10f))
                            .padding(12.dp)
                    ) {
                        Text("موجودی طبق پیامک: " + m(smsBal), fontSize = 12.sp)
                        Text("موجودی برنامه پس از ثبت: " + m(expected), fontSize = 12.sp)
                        Text(
                            if (diff == 0L) "✓ موجودی‌ها هم‌خوان است"
                            else "اختلاف: " + (if (diff > 0) "+" else "−") + m(kotlin.math.abs(diff)),
                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = if (diff == 0L) Green else Red,
                        )
                        if (diff != 0L) Hint("شاید تراکنشی ثبت نشده، مبلغ اشتباه است یا موجودی اولیه درست وارد نشده.")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = if (transfer) otherId != 0L else amount > 0, onClick = {
                if (transfer) {
                    vm.registerTransfer(p, otherId, note)
                    onDismiss()
                    return@TextButton
                }
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
