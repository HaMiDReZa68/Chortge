package com.example.smsaccounting

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.delay
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

/** اثر انگشت / بیومتریک با API خود سیستم (بدون کتابخانهٔ اضافه) */
object Bio {
    fun available(ctx: Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 28) return false
        return try {
            when {
                android.os.Build.VERSION.SDK_INT >= 30 ->
                    ctx.getSystemService(BiometricManager::class.java)
                        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
                android.os.Build.VERSION.SDK_INT >= 29 ->
                    @Suppress("DEPRECATION")
                    ctx.getSystemService(BiometricManager::class.java).canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
                else -> ctx.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_FINGERPRINT)
            }
        } catch (e: Exception) {
            false
        }
    }

    fun prompt(ctx: Context, onOk: () -> Unit) {
        if (android.os.Build.VERSION.SDK_INT < 28) return
        val ex = ctx.mainExecutor
        val b = BiometricPrompt.Builder(ctx)
            .setTitle("ورود به هومایه")
            .setNegativeButton("استفاده از رمز", ex) { _, _ -> }
        if (android.os.Build.VERSION.SDK_INT >= 30) b.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        b.build().authenticate(
            CancellationSignal(), ex,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    onOk()
                }
            },
        )
    }
}

// ---------------------------------------------------------------- رمز عددی

@Composable
fun PinPad(length: Int?, error: String?, enabled: Boolean = true, onSubmit: (String) -> Unit) {
    var digits by remember { mutableStateOf("") }
    val max = length ?: 8
    LaunchedEffect(error) { if (error != null) digits = "" }
    fun add(d: Char) {
        if (!enabled || digits.length >= max) return
        digits += d
        if (length != null && digits.length == length) {
            val v = digits
            digits = ""
            onSubmit(v)
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.height(28.dp)) {
            val n = length ?: digits.length
            for (i in 0 until n) {
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (i < digits.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        val rows = listOf("123", "456", "789")
        rows.forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                r.forEach { d -> Key(d.toString(), enabled) { add(d) } }
            }
            Spacer(Modifier.height(14.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            if (length == null) {
                Key("✓", enabled && digits.length >= 4) {
                    val v = digits
                    digits = ""
                    onSubmit(v)
                }
            } else {
                Spacer(Modifier.size(68.dp))
            }
            Key("0", enabled) { add('0') }
            Key("⌫", enabled) { if (digits.isNotEmpty()) digits = digits.dropLast(1) }
        }
    }
}

@Composable
private fun Key(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(68.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f))
    }
}

// ---------------------------------------------------------------- الگو

@Composable
fun PatternPad(error: String?, enabled: Boolean = true, onComplete: (String) -> Unit) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var sel by remember { mutableStateOf(listOf<Int>()) }
    var cur by remember { mutableStateOf<Offset?>(null) }
    val prim = MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.outlineVariant
    val bad = error != null && sel.isNotEmpty()
    LaunchedEffect(error) {
        if (error != null) {
            delay(700)
            sel = emptyList()
        }
    }
    fun center(i: Int): Offset {
        val cell = size.width / 3f
        return Offset((i % 3 + 0.5f) * cell, (i / 3 + 0.5f) * cell)
    }
    fun hit(o: Offset): Int? {
        for (i in 0 until 9) {
            val c = center(i)
            if ((c - o).getDistance() < size.width / 3f * 0.32f) return i
        }
        return null
    }
    Canvas(
        Modifier
            .size(270.dp)
            .pointerInput(enabled, error) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { o ->
                        sel = emptyList()
                        cur = o
                        hit(o)?.let { sel = listOf(it) }
                    },
                    onDrag = { change, _ ->
                        cur = change.position
                        val h = hit(change.position)
                        if (h != null && h !in sel) sel = sel + h
                    },
                    onDragEnd = {
                        cur = null
                        if (sel.isNotEmpty()) onComplete(sel.joinToString("-"))
                    },
                    onDragCancel = { cur = null },
                )
            }
            .onSizeChanged { size = it }
    ) {
        val col = if (bad) Color(0xFFE5484D) else prim
        for (k in 0 until sel.size - 1) {
            drawLine(col, center(sel[k]), center(sel[k + 1]), 8f, StrokeCap.Round)
        }
        val last = sel.lastOrNull()
        val c = cur
        if (last != null && c != null) drawLine(col, center(last), c, 8f, StrokeCap.Round)
        for (i in 0 until 9) {
            val on = i in sel
            drawCircle(if (on) col else idle, if (on) 20f else 14f, center(i))
            if (on) drawCircle(col.copy(alpha = 0.18f), 46f, center(i))
        }
    }
}

// ---------------------------------------------------------------- صفحهٔ قفل

private fun Context.activity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val type = prefs.lockType
    var error by remember { mutableStateOf<String?>(null) }
    var fails by remember { mutableStateOf(0) }
    var wait by remember { mutableStateOf(0) }
    val bio = prefs.lockBio && Bio.available(ctx)

    LaunchedEffect(Unit) {
        if (bio) Bio.prompt(ctx) { onUnlocked() }
    }
    LaunchedEffect(wait) {
        if (wait > 0) {
            delay(1000)
            wait -= 1
            if (wait == 0) {
                fails = 0
                error = null
            }
        }
    }
    fun attempt(secret: String) {
        if (prefs.checkLock(secret)) {
            onUnlocked()
        } else {
            fails += 1
            if (fails >= 5) {
                wait = 30
                error = "چند بار اشتباه بود؛ صبر کن"
            } else {
                error = if (type == "pin") "رمز اشتباه است" else "الگو اشتباه است"
            }
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Image(
            painterResource(R.drawable.logo_mask),
            contentDescription = "هومایه",
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
            contentScale = ContentScale.Fit,
            modifier = Modifier.height(80.dp),
        )
        Spacer(Modifier.height(18.dp))
        Text(
            if (type == "pin") "رمز را وارد کن" else "الگو را بکش",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (wait > 0) "دوباره تا " + Fa.digits(wait.toString()) + " ثانیه دیگر" else error ?: " ",
            color = Color(0xFFE5484D),
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(18.dp))
        if (type == "pin") {
            PinPad(prefs.lockLen, error, enabled = wait == 0) { attempt(it) }
        } else {
            PatternPad(error, enabled = wait == 0) { attempt(it) }
        }
        Spacer(Modifier.weight(1f))
        if (bio) {
            SoftButton("ورود با اثر انگشت") { Bio.prompt(ctx) { onUnlocked() } }
        }
    }
}

// ---------------------------------------------------------------- تنظیمات قفل

@Composable
fun LockSettingsPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var type by remember { mutableStateOf(prefs.lockType) }
    var bio by remember { mutableStateOf(prefs.lockBio) }
    var delaySec by remember { mutableStateOf(prefs.lockDelay) }
    var setup by remember { mutableStateOf<String?>(null) } // "pin" یا "pattern"
    var first by remember { mutableStateOf<String?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    val bioOk = remember { Bio.available(ctx) }

    PageScaffold("قفل برنامه", { if (setup != null) { setup = null; first = null; err = null } else onBack() }) {
        val s = setup
        if (s == null) {
            SectionCard {
                Text(
                    when (type) {
                        "pin" -> "قفل فعال: رمز عددی"
                        "pattern" -> "قفل فعال: الگو"
                        else -> "قفل غیرفعال است"
                    },
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                ChipRow {
                    Chip("بدون قفل", type == "none") {
                        prefs.clearLock()
                        type = "none"
                        bio = false
                    }
                    Chip("رمز عددی", type == "pin") { setup = "pin"; first = null; err = null }
                    Chip("الگو", type == "pattern") { setup = "pattern"; first = null; err = null }
                }
            }
            if (type != "none") {
                if (bioOk) {
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("ورود با اثر انگشت", fontWeight = FontWeight.Bold)
                                Hint("رمز یا الگو همیشه به‌عنوان پشتیبان می‌ماند.")
                            }
                            Switch(checked = bio, onCheckedChange = {
                                bio = it
                                prefs.lockBio = it
                            })
                        }
                    }
                }
                SectionCard {
                    Text("قفل شدن بعد از خروج از برنامه", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    ChipRow {
                        listOf(0 to "فوری", 30 to "۳۰ ثانیه", 60 to "۱ دقیقه", 300 to "۵ دقیقه").forEach { (v, l) ->
                            Chip(l, delaySec == v) {
                                delaySec = v
                                prefs.lockDelay = v
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Hint("وقتی برای ذخیره یا انتخاب فایل از برنامه بیرون می‌روی، زمان کوتاه ممکن است قفل را زودتر فعال کند.")
                }
            }
            Hint("اگر رمز یا الگو را فراموش کنی، راه بازیابی وجود ندارد و باید برنامه را پاک و دوباره نصب کنی. فایل پشتیبان نگه دار.")
        } else {
            SectionCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (first == null) (if (s == "pin") "رمز جدید را وارد کن (۴ تا ۸ رقم)" else "الگوی جدید را بکش (حداقل ۴ نقطه)")
                        else "برای تأیید دوباره وارد کن",
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(err ?: " ", color = Color(0xFFE5484D), fontSize = 13.sp)
                    Spacer(Modifier.height(14.dp))
                    fun handle(v: String) {
                        val f = first
                        if (f == null) {
                            if (s == "pattern" && v.split("-").size < 4) {
                                err = "حداقل ۴ نقطه را وصل کن"
                            } else {
                                first = v
                                err = null
                            }
                        } else if (f == v) {
                            prefs.setLock(s, v)
                            type = s
                            setup = null
                            first = null
                            err = null
                        } else {
                            err = "با اولی یکی نیست؛ دوباره"
                            first = null
                        }
                    }
                    if (s == "pin") PinPad(null, err) { handle(it) } else PatternPad(err) { handle(it) }
                }
            }
        }
    }
}
