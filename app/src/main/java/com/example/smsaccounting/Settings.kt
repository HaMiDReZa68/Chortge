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
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit, onNav: (Page) -> Unit) {
    val ctx = LocalContext.current
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    PageScaffold("تنظیمات", onBack) {
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔔", fontSize = 22.sp)
                Spacer(Modifier.width(10.dp))
                Text("یادآوری روزانه", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = vm.reminderOn,
                    onCheckedChange = { on ->
                        if (on && !Notifs.canPost(ctx)) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        vm.applyReminder(on, vm.reminderH, vm.reminderM)
                    },
                )
            }
            Spacer(Modifier.height(6.dp))
            Hint("هر روز سر ساعت انتخابی، یک اعلان با خلاصهٔ هزینهٔ امروز نشان داده می‌شود.")
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        TimePickerDialog(
                            ctx,
                            { _, h, m -> vm.applyReminder(vm.reminderOn, h, m) },
                            vm.reminderH, vm.reminderM, true,
                        ).show()
                    }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("ساعت یادآوری", modifier = Modifier.weight(1f))
                Text(
                    Fa.digits("%02d:%02d".format(vm.reminderH, vm.reminderM)),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(8.dp))
            Hint("اگر اعلان‌ها یا «هشدار و یادآور دقیق» بسته باشد، اعلان‌ها دیر یا بدون نمایش می‌رسند. در بعضی گوشی‌ها (شیائومی، سامسونگ و ...) محدودیت باتری اپ را هم بردار.")
        }
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("واحد پول", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Chip("تومان", vm.toman) { vm.changeToman(true) }
                Spacer(Modifier.width(8.dp))
                Chip("ریال", !vm.toman) { vm.changeToman(false) }
            }
        }
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("به‌روزرسانی برنامه", fontWeight = FontWeight.Bold)
                    Hint("نسخهٔ فعلی: " + Fa.digits((Updater.currentCode(ctx) - 100).toString()) +
                        (if (vm.update != null) " · نسخهٔ جدید موجود است" else ""))
                }
                if (vm.update != null) {
                    SoftButton("دانلود") { vm.installUpdate() }
                } else {
                    SoftButton("بررسی") { vm.checkUpdate(true) }
                }
            }
        }
        SectionCard {
            Text("ظاهر", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            ChipRow {
                listOf(0 to "روشن", 1 to "تیره", 2 to "طبق گوشی").forEach { (v, l) ->
                    Chip(l, ThemeState.mode == v) {
                        ThemeState.mode = v
                        Prefs(ctx).themeMode = v
                    }
                }
            }
        }
        listOf(
            Triple("🔒", "قفل برنامه (رمز، الگو، اثر انگشت)", Page.LOCK),
            Triple("🏦", "حساب‌ها و سرشماره‌های پیامک", Page.ACCOUNTS),
            Triple("🏷️", "دسته‌بندی‌ها و زیرشاخه‌ها", Page.CATEGORIES),
            Triple("📤", "خروجی اکسل و PDF", Page.EXPORT),
            Triple("☁️", "پشتیبان‌گیری و بازیابی", Page.BACKUP),
        ).forEach { (icon, label, page) ->
            SectionCard(Modifier.clickable { onNav(page) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(icon, fontSize = 22.sp)
                    Spacer(Modifier.width(12.dp))
                    Text(label, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("‹", fontSize = 20.sp)
                }
            }
        }
    }
}
