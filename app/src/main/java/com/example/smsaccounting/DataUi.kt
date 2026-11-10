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

@Composable
fun ExportPage(vm: MainViewModel, onBack: () -> Unit) {
    val now = System.currentTimeMillis()
    val ym = Jalali.ymOf(now)
    var kind by rememberSaveable { mutableStateOf(0) }
    var from by rememberSaveable { mutableStateOf(Jalali.range(ym).first) }
    var to by rememberSaveable { mutableStateOf(Jalali.startOfDay(now)) } // روز آخر (شامل)
    var accountId by rememberSaveable { mutableStateOf(0L) }
    var pend by remember { mutableStateOf<ExportSpec?>(null) }

    val xlsxMime = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    val xlsxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(xlsxMime)) { uri ->
        val s = pend
        if (uri != null && s != null) vm.saveExport(s, true, uri)
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val s = pend
        if (uri != null && s != null) vm.saveExport(s, false, uri)
    }
    fun spec() = ExportSpec(kind, from, Jalali.startOfDay(to) + 86_400_000L, accountId, vm.toman)

    PageScaffold("خروجی اکسل و PDF", onBack) {
        SectionCard {
            Text("نوع گزارش", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            ChipRow {
                Chip("تراکنش‌های کامل", kind == 0) { kind = 0 }
                Chip("دخل و خرج بر اساس تاریخ", kind == 1) { kind = 1 }
            }
        }
        SectionCard {
            DateTimeRow("از تاریخ", from, withTime = false) { from = Jalali.startOfDay(it) }
            Spacer(Modifier.height(10.dp))
            DateTimeRow("تا تاریخ", to, withTime = false) { to = Jalali.startOfDay(it) }
        }
        SectionCard {
            Text("حساب", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            ChipRow {
                Chip("همهٔ حساب‌ها", accountId == 0L) { accountId = 0L }
                vm.accounts.forEach { a -> Chip(a.name, accountId == a.id) { accountId = a.id } }
            }
        }
        val valid = to >= from
        if (!valid) Hint("تاریخ پایان باید بعد از تاریخ شروع باشد.")
        listOf(true to "اکسل (XLSX)", false to "PDF").forEach { (x, label) ->
            SectionCard {
                Text(label, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftButton("ذخیره در گوشی", Modifier.weight(1f), enabled = valid && !vm.busy) {
                        val s = spec()
                        pend = s
                        if (x) xlsxLauncher.launch(vm.exportName(s, true)) else pdfLauncher.launch(vm.exportName(s, false))
                    }
                    SoftButton("ارسال / ذخیره در ابر", Modifier.weight(1f), enabled = valid && !vm.busy) {
                        vm.shareExport(spec(), x)
                    }
                }
            }
        }
        if (vm.busy) Hint("در حال ساخت فایل…")
    }
}

@Composable
fun BackupPage(vm: MainViewModel, onBack: () -> Unit) {
    var confirmRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.saveBackup(uri)
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmRestore = uri
    }
    PageScaffold("پشتیبان‌گیری و بازیابی", onBack) {
        SectionCard {
            Text("ذخیره در حافظهٔ گوشی", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Hint("فایل پشتیبان را در پوشه‌ای که انتخاب می‌کنی ذخیره می‌کند.")
            Spacer(Modifier.height(10.dp))
            PrimaryButton("ذخیرهٔ فایل پشتیبان", enabled = !vm.busy) { save.launch(Backup.fileName()) }
        }
        SectionCard {
            Text("گوگل درایو / دراپ‌باکس", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Hint("منوی اشتراک‌گذاری باز می‌شود؛ گوگل درایو یا دراپ‌باکس را انتخاب کن تا فایل در حساب خودت آپلود شود. برای بازیابی هم همان فایل را از «بازیابی از فایل» (از درایو یا دراپ‌باکس) انتخاب کن.")
            Spacer(Modifier.height(10.dp))
            PrimaryButton("ارسال به درایو / دراپ‌باکس", enabled = !vm.busy) { vm.shareBackup() }
        }
        SectionCard {
            Text("بازیابی از فایل", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Hint("همهٔ اطلاعات فعلی با محتوای فایل جایگزین می‌شود.")
            Spacer(Modifier.height(10.dp))
            SoftButton("انتخاب فایل پشتیبان", Modifier.fillMaxWidth(), enabled = !vm.busy) {
                open.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*"))
            }
        }
        SectionCard {
            Text("پشتیبان خودکار گوگل", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Hint("پشتیبان‌گیری خودکار اندروید (Google One) برای این اپ فعال است و اگر در تنظیمات گوشی روشن باشد، اطلاعات به حساب گوگل تو ذخیره می‌شود. برای اطمینان، فایل دستی هم نگه دار.")
        }
    }
    confirmRestore?.let { uri ->
        ConfirmDialog("بازیابی", "اطلاعات فعلی حذف و با فایل پشتیبان جایگزین شود؟", confirm = "بازیابی", onDismiss = { confirmRestore = null }) {
            vm.restoreBackup(uri)
        }
    }
}
