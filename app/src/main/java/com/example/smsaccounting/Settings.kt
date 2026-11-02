package com.example.smsaccounting

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    var renaming by remember { mutableStateOf<BankInfo?>(null) }

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
            Text("تنظیمات", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---------- یادآوری روزانه ----------
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🔔", fontSize = 22.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "یادآوری روزانه",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = vm.reminderOn,
                            onCheckedChange = { on ->
                                if (on && Build.VERSION.SDK_INT >= 33 &&
                                    ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                                    PackageManager.PERMISSION_GRANTED
                                ) {
                                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                vm.applyReminder(on, vm.reminderH, vm.reminderM)
                            },
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "هر روز سر ساعت انتخابی، یک اعلان با خلاصهٔ هزینهٔ امروز نشان داده می‌شود.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.background)
                            .clickable {
                                TimePickerDialog(
                                    ctx,
                                    { _, h, m -> vm.applyReminder(vm.reminderOn, h, m) },
                                    vm.reminderH,
                                    vm.reminderM,
                                    true,
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
                    Text(
                        "اگر اعلان‌ها یا «هشدار و یادآور دقیق» برای اپ بسته باشد، یادآوری با تأخیر یا بدون نمایش می‌رسد. " +
                            "در بعضی گوشی‌ها (شیائومی، سامسونگ و ...) باید محدودیت باتری اپ را هم بردارید.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ---------- حساب‌ها و بانک‌ها ----------
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("حساب‌ها و بانک‌ها", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "پیامک هر بانکی که خاموش باشد خوانده نمی‌شود و تراکنش‌هایش در گزارش‌ها نمی‌آید.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))

                    AccountRow(
                        icon = "💵",
                        title = "نقدی",
                        subtitle = "با دکمهٔ ＋ در صفحهٔ اصلی ثبت می‌شود",
                    ) {
                        Text("همیشه فعال", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    if (vm.banks.isEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.background)
                        Text(
                            "هنوز بانکی شناسایی نشده. اجازهٔ پیامک را بده و همگام‌سازی کن.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                    vm.banks.forEach { b ->
                        HorizontalDivider(color = MaterialTheme.colorScheme.background)
                        AccountRow(
                            icon = "🏦",
                            title = b.name,
                            subtitle = "فرستنده: ${b.sender} · " + Fa.digits(b.count.toString()) + " تراکنش",
                        ) {
                            TextButton(onClick = { renaming = b }) { Text("✎ نام") }
                            Switch(
                                checked = b.enabled,
                                onCheckedChange = { vm.toggleBank(b.sender, it) },
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { vm.importSms(true) },
                        enabled = !vm.busy,
                        colors = ButtonDefaults.buttonColors(containerColor = Teal1),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (vm.busy) "در حال خواندن…" else "بررسی مجدد همهٔ پیامک‌ها", color = Color.White)
                    }
                    vm.status?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    val r = renaming
    if (r != null) {
        var name by remember(r.sender) { mutableStateOf(r.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("نام بانک", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.renameBank(r.sender, name)
                    renaming = null
                }) { Text("ذخیره") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("انصراف") } },
        )
    }
}

@Composable
private fun AccountRow(
    icon: String,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Teal1.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) { Text(icon, fontSize = 20.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

/** ثبت تراکنش نقدی */
@Composable
fun AddCashDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    var type by remember { mutableStateOf(TxType.EXPENSE) }
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var picked by remember(type) { mutableStateOf<String?>(null) }

    val cats = if (type == TxType.INCOME) Categorizer.incomeCategories else Categorizer.expenseCategories
    val effective = picked ?: Categorizer.categorize(type, note, note, emptyMap())
    val amount = Norm.normalize(amountText).replace(",", "").toLongOrNull() ?: 0L
    val unit = if (vm.toman) "تومان" else "ریال"

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("ثبت تراکنش نقدی", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ModeToggle(type) { type = it }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("مبلغ ($unit)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("توضیح (مثلاً نانوایی)") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("دسته", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    cats.forEach { c ->
                        val sel = c == effective
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (sel) Look.color(c).copy(alpha = 0.22f) else MaterialTheme.colorScheme.background)
                                .clickable { picked = c }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(Look.icon(c), fontSize = 14.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                c,
                                fontSize = 12.sp,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = amount > 0,
                onClick = {
                    vm.addCash(type, if (vm.toman) amount * 10 else amount, note, effective)
                    onDismiss()
                },
            ) { Text("ثبت") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}
