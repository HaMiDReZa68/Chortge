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
fun CategoriesPage(vm: MainViewModel, onBack: () -> Unit) {
    var type by remember { mutableStateOf(TxType.EXPENSE) }
    var adding by remember { mutableStateOf<Long?>(null) } // 0 = دستهٔ اصلی، غیر صفر = زیرشاخهٔ آن
    var delete by remember { mutableStateOf<Category?>(null) }
    val tops = vm.cats.filter { it.type == type && it.parentId == 0L }

    PageScaffold("دسته‌بندی‌ها", onBack, action = { RoundBtn("＋") { adding = 0L } }) {
        ModeToggle(type) { type = it }
        Hint(if (type == TxType.EXPENSE) "دسته‌های خرج" else "دسته‌های دخل")
        tops.forEach { c ->
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CatIcon(c.name, 40)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, fontWeight = FontWeight.Bold)
                        if (c.keywords.isNotBlank()) Hint("کلمات: " + c.keywords)
                    }
                    TextButton(onClick = { adding = c.id }) { Text("＋ زیرشاخه") }
                    TextButton(onClick = { delete = c }) { Text("🗑", color = Red) }
                }
                vm.cats.filter { it.parentId == c.id }.forEach { s ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("› " + s.name, modifier = Modifier.weight(1f), fontSize = 14.sp)
                        if (s.keywords.isNotBlank()) Hint(s.keywords)
                        TextButton(onClick = { delete = s }) { Text("حذف", color = Red) }
                    }
                }
            }
        }
        if (tops.isEmpty()) Hint("دسته‌ای نیست. با ＋ بالا یکی بساز.")
    }

    adding?.let { parent ->
        var name by remember(parent, type) { mutableStateOf("") }
        var icon by remember(parent, type) { mutableStateOf("") }
        var kw by remember(parent, type) { mutableStateOf("") }
        val parentName = vm.cats.firstOrNull { it.id == parent }?.name
        AlertDialog(
            onDismissRequest = { adding = null },
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    if (parentName == null) "دستهٔ جدید" else "زیرشاخهٔ جدید برای «$parentName»",
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Field(name, { name = it }, "نام")
                    if (parentName == null) {
                        Spacer(Modifier.height(8.dp))
                        Field(icon, { icon = it }, "آیکون (یک ایموجی، اختیاری)")
                    }
                    Spacer(Modifier.height(8.dp))
                    Field(kw, { kw = it }, "کلمات کلیدی برای تشخیص خودکار (با ویرگول جدا کن، اختیاری)")
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    vm.addCategory(name, type, parent, icon.ifBlank { if (parent == 0L) "🏷️" else "" }, kw)
                    adding = null
                }) { Text("افزودن") }
            },
            dismissButton = { TextButton(onClick = { adding = null }) { Text("انصراف") } },
        )
    }
    delete?.let { c ->
        ConfirmDialog(
            "حذف دسته",
            "«${c.name}» حذف شود؟ تراکنش‌های قبلی همان نام را نگه می‌دارند." +
                if (c.parentId == 0L) " زیرشاخه‌هایش هم حذف می‌شوند." else "",
            onDismiss = { delete = null },
        ) { vm.deleteCategory(c.id) }
    }
}
