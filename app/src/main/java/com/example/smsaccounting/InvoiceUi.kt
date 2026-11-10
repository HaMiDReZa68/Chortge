package com.example.smsaccounting

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun money(vm: MainViewModel, r: Long) = InvFmt.money(r, vm.invSet.toman)

// ============================================================ لیست فاکتورها

@Composable
fun InvoicesTab(vm: MainViewModel, onSettings: () -> Unit, onEdit: (Invoice?) -> Unit) {
    var filter by remember { mutableStateOf(0) } // 0 همه، 1 پرداخت‌نشده، 2 پرداخت‌شده
    var delete by remember { mutableStateOf<Invoice?>(null) }
    val unit = InvFmt.unit(vm.invSet.toman)
    val all = vm.invoices
    val shown = all.filter { filter == 0 || (filter == 1 && !it.paid) || (filter == 2 && it.paid) }
    val unpaid = all.filter { !it.paid }.sumOf { it.totals().total }
    val sales = all.sumOf { it.totals().total }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    TopBar("فاکتورها") { RoundBtn("⚙", onSettings) }
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (vm.invSet.name.isBlank()) {
            SectionCard(Modifier.clickable { onSettings() }) {
                Text("ابتدا اطلاعات فروشگاه را وارد کن", fontWeight = FontWeight.Bold)
                Hint("نام، آدرس، تلفن، لوگو، مهر و شمارهٔ شروع فاکتور در تنظیمات فاکتور. (برای باز کردن لمس کن)")
            }
        }
        PrimaryButton("＋ فاکتور جدید") { onEdit(null) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat("جمع فاکتورها", money(vm, sales) + " " + unit, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            MiniStat("پرداخت‌نشده", money(vm, unpaid) + " " + unit, Red, Modifier.weight(1f))
        }
        ComboSingle(
            "نمایش",
            listOf(0 to "همهٔ فاکتورها", 1 to "پرداخت‌نشده", 2 to "پرداخت‌شده"),
            filter,
        ) { filter = it }
        if (shown.isEmpty()) Hint("فاکتوری وجود ندارد.")
        shown.forEach { inv ->
            val t = inv.totals().total
            SectionCard(Modifier.clickable { onEdit(inv) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(inv.custName.ifBlank { "بدون نام مشتری" }, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "فاکتور " + Fa.digits(inv.no.toString()) + " · " + Fa.digits(Jalali.full(inv.ts)),
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(money(vm, t) + " " + unit, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Chip(if (inv.paid) "پرداخت‌شده" else "پرداخت‌نشده", inv.paid, if (inv.paid) Green else Red) { vm.toggleInvoicePaid(inv) }
                    Spacer(Modifier.weight(1f))
                    SoftButton("PDF") { vm.showInvoicePdf(inv) }
                    Spacer(Modifier.width(8.dp))
                    SoftButton("حذف") { delete = inv }
                }
            }
        }
        Spacer(Modifier.height(110.dp))
    }
    }

    delete?.let { d ->
        ConfirmDialog("حذف فاکتور", "فاکتور ${Fa.digits(d.no.toString())} حذف شود؟", onDismiss = { delete = null }) {
            vm.deleteInvoice(d); delete = null
        }
    }
    vm.invPdf?.let { InvoicePdfDialog(vm, it) { vm.showInvoicePdf(null) } }
}

// ============================================================ تنظیمات فاکتور

@Composable
private fun ImagePicker(vm: MainViewModel, title: String, hint: String, kind: String) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.setInvImage(uri, kind)
    }
    val ctx = LocalContext.current
    val bmp = remember(vm.invImgVer) { InvImages.bitmap(ctx, kind) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(72.dp)
                .clip(if (kind == "logo") CircleShape else RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, if (kind == "logo") CircleShape else RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (bmp != null) Image(bmp.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp))
            else Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Hint(hint)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftButton(if (bmp == null) "انتخاب تصویر" else "تغییر") { picker.launch("image/*") }
                if (bmp != null) SoftButton("حذف") { vm.removeInvImage(kind) }
            }
        }
    }
}

@Composable
fun InvoiceSettingsPage(vm: MainViewModel, onBack: () -> Unit) {
    val s = vm.invSet
    var name by remember { mutableStateOf(s.name) }
    var addr by remember { mutableStateOf(s.address) }
    var phone by remember { mutableStateOf(s.phone) }
    var next by remember { mutableStateOf(s.next.toString()) }
    var toman by remember { mutableStateOf(s.toman) }
    var paper by remember { mutableStateOf(s.paper) }
    var footer by remember { mutableStateOf(s.footer) }

    PageScaffold("تنظیمات فاکتور", onBack) {
        SectionCard {
            Text("اطلاعات فروشنده", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Field(name, { name = it }, "نام شرکت / فروشگاه")
            Spacer(Modifier.height(8.dp))
            Field(addr, { addr = it }, "آدرس", singleLine = false)
            Spacer(Modifier.height(8.dp))
            Field(phone, { phone = it }, "تلفن", number = true)
        }
        SectionCard {
            ImagePicker(vm, "لوگو", "یک بار انتخاب کن؛ بالای همهٔ فاکتورها می‌آید.", "logo")
            Spacer(Modifier.height(14.dp))
            ImagePicker(vm, "مهر و امضا", "تصویر مهر یا امضا (بهتر است PNG با پس‌زمینهٔ شفاف).", "stamp")
        }
        SectionCard {
            Text("شماره‌گذاری", fontWeight = FontWeight.Bold)
            Hint("شمارهٔ فاکتور بعدی؛ بعد از هر فاکتور خودکار یکی بالا می‌رود و موقع ساخت فاکتور هم قابل ویرایش است.")
            Spacer(Modifier.height(8.dp))
            Field(next, { next = it }, "شمارهٔ فاکتور بعدی", number = true)
        }
        SectionCard {
            ComboSingle("واحد پول همهٔ فاکتورها", listOf(false to "ریال", true to "تومان"), toman) { toman = it }
            Hint("با تغییر واحد، مبلغ همهٔ فاکتورهای قبلی هم با همین واحد نمایش داده می‌شود.")
            Spacer(Modifier.height(10.dp))
            ComboSingle("اندازهٔ پیش‌فرض کاغذ", listOf("A4" to "A4", "A5" to "A5"), paper) { paper = it }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("نمایش «ایجاد شده با هومایه» پایین فاکتور", modifier = Modifier.weight(1f), fontSize = 13.sp)
                Switch(checked = footer, onCheckedChange = { footer = it })
            }
        }
        PrimaryButton("ذخیره") {
            vm.saveInvSettings(
                InvSettings(name.trim(), addr.trim(), phone.trim(), parseAmount(next).coerceAtLeast(1L), toman, paper, footer)
            )
            onBack()
        }
    }
}

// ============================================================ فرم فاکتور

@Composable
fun InvoiceEditPage(vm: MainViewModel, initial: Invoice?, onBack: () -> Unit) {
    val toman = vm.invSet.toman
    val mul = if (toman) 10.0 else 1.0
    val unit = InvFmt.unit(toman)
    fun disp(rial: Long): String = if (rial == 0L) "" else (if (toman) Math.round(rial / 10.0) else rial).toString()
    fun pct(d: Double): String = if (d == 0.0) "" else d.toString().removeSuffix(".0")

    var no by remember { mutableStateOf((initial?.no ?: vm.invSet.next).toString()) }
    var ts by remember { mutableStateOf(initial?.ts ?: System.currentTimeMillis()) }
    var custId by remember { mutableStateOf(0L) }
    var cName by remember { mutableStateOf(initial?.custName ?: "") }
    var cPhone by remember { mutableStateOf(initial?.custPhone ?: "") }
    var cAddr by remember { mutableStateOf(initial?.custAddr ?: "") }
    var saveCust by remember { mutableStateOf(initial == null) }
    val items = remember { mutableStateListOf<InvItem>().apply { initial?.let { addAll(it.items) } } }
    var eDesc by remember { mutableStateOf("") }
    var eQty by remember { mutableStateOf("1") }
    var ePrice by remember { mutableStateOf("") }
    var eNote by remember { mutableStateOf("") }
    var editIdx by remember { mutableStateOf(-1) }
    var discMode by remember { mutableStateOf(initial?.discMode ?: 0) }
    var discText by remember {
        mutableStateOf(initial?.let { if (it.discMode == 0) pct(it.discVal) else disp(it.discVal.toLong()) } ?: "")
    }
    var taxMode by remember { mutableStateOf(initial?.taxMode ?: 0) }
    var taxText by remember {
        mutableStateOf(initial?.let { if (it.taxMode == 0) pct(it.taxVal) else disp(it.taxVal.toLong()) } ?: "")
    }
    var debtText by remember { mutableStateOf(initial?.let { disp(it.debt) } ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var paid by remember { mutableStateOf(initial?.paid ?: false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun build(): Invoice = Invoice(
        id = initial?.id ?: 0L,
        no = parseAmount(no).coerceAtLeast(1L),
        ts = ts,
        custName = cName.trim(), custPhone = cPhone.trim(), custAddr = cAddr.trim(),
        items = items.toList(),
        discMode = discMode,
        discVal = if (discMode == 0) InvFmt.parse(discText) else InvFmt.parse(discText) * mul,
        taxMode = taxMode,
        taxVal = if (taxMode == 0) InvFmt.parse(taxText) else InvFmt.parse(taxText) * mul,
        debt = Math.round(InvFmt.parse(debtText) * mul),
        notes = notes.trim(),
        paid = paid,
    )
    val draft = build()
    val tot = draft.totals()

    PageScaffold(if (initial == null) "فاکتور جدید" else "ویرایش فاکتور", onBack) {
        SectionCard {
            Field(no, { no = it }, "شمارهٔ فاکتور", number = true)
            Spacer(Modifier.height(10.dp))
            DateTimeRow("تاریخ فاکتور (قابل تغییر)", ts, false) { ts = it }
        }
        SectionCard {
            Text("مشتری", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (vm.customers.isNotEmpty()) {
                ComboSingle(
                    "انتخاب از مشتری‌های ذخیره‌شده",
                    listOf(0L to "مشتری جدید") + vm.customers.map { it.id to it.name },
                    custId,
                ) { id ->
                    custId = id
                    vm.customers.firstOrNull { it.id == id }?.let { cName = it.name; cPhone = it.phone; cAddr = it.address; saveCust = false }
                    if (id == 0L) { cName = ""; cPhone = ""; cAddr = ""; saveCust = true }
                }
                Spacer(Modifier.height(8.dp))
            }
            Field(cName, { cName = it }, "نام مشتری")
            Spacer(Modifier.height(8.dp))
            Field(cPhone, { cPhone = it }, "تلفن", number = true)
            Spacer(Modifier.height(8.dp))
            Field(cAddr, { cAddr = it }, "آدرس", singleLine = false)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ذخیره در فهرست مشتری‌ها", modifier = Modifier.weight(1f), fontSize = 13.sp)
                Switch(checked = saveCust, onCheckedChange = { saveCust = it })
            }
            if (custId != 0L) {
                SoftButton("حذف این مشتری از فهرست") { vm.deleteCustomer(custId); custId = 0L }
            }
        }
        SectionCard {
            Text(if (editIdx >= 0) "ویرایش ردیف ${Fa.digits((editIdx + 1).toString())}" else "افزودن ردیف", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Field(eDesc, { eDesc = it }, "شرح")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(eQty, { eQty = it }, "تعداد", number = true, modifier = Modifier.weight(1f))
                Field(ePrice, { ePrice = it }, "مبلغ واحد ($unit)", number = true, modifier = Modifier.weight(1.6f))
            }
            val rowTotal = Math.round(InvFmt.parse(eQty) * InvFmt.parse(ePrice) * mul)
            Spacer(Modifier.height(6.dp))
            Hint("قیمت کل: " + InvFmt.money(rowTotal, toman) + " " + unit)
            Spacer(Modifier.height(6.dp))
            Field(eNote, { eNote = it }, "توضیحات ردیف (اختیاری)")
            Spacer(Modifier.height(10.dp))
            PrimaryButton(if (editIdx >= 0) "ذخیرهٔ ردیف" else "＋ افزودن به جدول", enabled = eDesc.isNotBlank() && InvFmt.parse(ePrice) > 0) {
                val it = InvItem(eDesc.trim(), InvFmt.parse(eQty).takeIf { q -> q > 0 } ?: 1.0, Math.round(InvFmt.parse(ePrice) * mul), eNote.trim())
                if (editIdx >= 0) items[editIdx] = it else items.add(it)
                eDesc = ""; eQty = "1"; ePrice = ""; eNote = ""; editIdx = -1
            }
        }
        if (items.isNotEmpty()) {
            SectionCard {
                Text("اقلام فاکتور", fontWeight = FontWeight.Bold)
                items.forEachIndexed { i, it ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                editIdx = i; eDesc = it.desc; eQty = it.qty.toString().removeSuffix(".0")
                                ePrice = disp(it.unit); eNote = it.note
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(Fa.digits("${i + 1}. ") + it.desc, fontSize = 14.sp)
                            Text(
                                InvFmt.num(it.qty) + " × " + InvFmt.money(it.unit, toman) + (if (it.note.isNotBlank()) " · " + it.note else ""),
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(InvFmt.money(it.total, toman), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        TextButton(onClick = { items.removeAt(i); if (editIdx == i) editIdx = -1 }) { Text("🗑", color = Red) }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
        SectionCard {
            Text("تخفیف", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { ComboSingle("نوع", listOf(0 to "درصد", 1 to "مبلغ ($unit)"), discMode) { discMode = it } }
                Field(discText, { discText = it }, if (discMode == 0) "درصد" else "مبلغ", number = true, modifier = Modifier.weight(1f))
            }
            if (tot.disc > 0) Hint("تخفیف: " + InvFmt.money(tot.disc, toman) + " " + unit)
            Spacer(Modifier.height(12.dp))
            Text("مالیات", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { ComboSingle("نوع", listOf(0 to "درصد", 1 to "مبلغ ($unit)"), taxMode) { taxMode = it } }
                Field(taxText, { taxText = it }, if (taxMode == 0) "درصد" else "مبلغ", number = true, modifier = Modifier.weight(1f))
            }
            if (tot.tax > 0) Hint("مالیات: " + InvFmt.money(tot.tax, toman) + " " + unit + " (روی مبلغ بعد از تخفیف)")
            Spacer(Modifier.height(12.dp))
            Field(debtText, { debtText = it }, "بدهی قبلی مشتری ($unit)", number = true)
            Spacer(Modifier.height(8.dp))
            Field(notes, { notes = it }, "توضیحات (در فاکتور نمایش داده می‌شود)", singleLine = false)
            Spacer(Modifier.height(8.dp))
            Chip(if (paid) "پرداخت‌شده" else "پرداخت‌نشده", paid, if (paid) Green else Red) { paid = !paid }
        }
        SectionCard {
            TotalLine("جمع اقلام", tot.sub, toman, unit)
            if (tot.disc > 0) TotalLine("تخفیف", -tot.disc, toman, unit, Red)
            if (tot.tax > 0) TotalLine("مالیات", tot.tax, toman, unit)
            if (tot.debt > 0) TotalLine("بدهی قبلی", tot.debt, toman, unit)
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row {
                Text("مبلغ قابل پرداخت", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(InvFmt.money(tot.total, toman) + " " + unit, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
        val ok = items.isNotEmpty()
        PrimaryButton("ذخیره و نمایش PDF", enabled = ok) { vm.saveInvoice(build(), saveCust, true); onBack() }
        SoftButton("فقط ذخیره", Modifier.fillMaxWidth(), enabled = ok) { vm.saveInvoice(build(), saveCust, false); onBack() }
        if (initial != null) SoftButton("حذف فاکتور", Modifier.fillMaxWidth()) { confirmDelete = true }
        Spacer(Modifier.height(20.dp))
    }
    if (confirmDelete && initial != null) {
        ConfirmDialog("حذف فاکتور", "این فاکتور حذف شود؟", onDismiss = { confirmDelete = false }) {
            vm.deleteInvoice(initial); confirmDelete = false; onBack()
        }
    }
}

@Composable
private fun TotalLine(label: String, v: Long, toman: Boolean, unit: String, color: Color = Color.Unspecified) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 13.sp)
        Text((if (v < 0) "−" else "") + InvFmt.money(kotlin.math.abs(v), toman) + " " + unit, fontSize = 13.sp, color = color)
    }
}

// ============================================================ پیش‌نمایش و خروجی PDF

@Composable
fun InvoicePdfDialog(vm: MainViewModel, inv: Invoice, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var paper by remember { mutableStateOf(vm.invSet.paper) }
    var bytes by remember { mutableStateOf<ByteArray?>(null) }
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    val name = "invoice-" + inv.no + ".pdf"
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val b = bytes
        if (uri != null && b != null) vm.say(if (Share.write(ctx, uri, b)) "PDF ذخیره شد" else "ذخیره نشد")
    }
    LaunchedEffect(paper, inv, vm.invImgVer, vm.invSet) {
        val b = withContext(Dispatchers.Default) { InvoicePdf.build(ctx, inv, vm.invSet, paper) }
        val p = withContext(Dispatchers.Default) { InvoicePdf.previews(ctx, b, 900) }
        bytes = b
        pages = p
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("فاکتور " + Fa.digits(inv.no.toString()), fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ComboSingle("اندازهٔ کاغذ", listOf("A4" to "A4", "A5" to "A5"), paper) { paper = it }
                Spacer(Modifier.height(10.dp))
                if (pages.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                pages.forEach {
                    Image(
                        it.asImageBitmap(), null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        contentScale = ContentScale.FillWidth,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftButton("ذخیره", Modifier.weight(1f), enabled = bytes != null) { saver.launch(name) }
                    SoftButton("اشتراک‌گذاری", Modifier.weight(1f), enabled = bytes != null) {
                        bytes?.let { Share.send(ctx, name, "application/pdf", it) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("بستن") } },
    )
}
