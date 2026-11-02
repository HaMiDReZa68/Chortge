package com.example.smsaccounting

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

object Look {
    private const val TRANSPORT = "حمل‌ونقل"

    val catColor: Map<String, Color> = mapOf(
        "خوراک" to Color(0xFFFF8A65),
        "پوشاک" to Color(0xFFBA68C8),
        "بنزین" to Color(0xFFFFB300),
        TRANSPORT to Color(0xFF29B6F6),
        "قبوض" to Color(0xFF26A69A),
        "شارژ و اینترنت" to Color(0xFF5C6BC0),
        "درمان" to Color(0xFFEF5350),
        "تفریح" to Color(0xFFEC407A),
        "آموزش" to Color(0xFF8D6E63),
        "خانه" to Color(0xFF66BB6A),
        "بیمه" to Color(0xFF78909C),
        "خرید اینترنتی" to Color(0xFF7E57C2),
        "انتقال وجه" to Color(0xFF90A4AE),
        "سایر" to Color(0xFFBDBDBD),
        "حقوق" to Color(0xFF43A047),
        "درآمد" to Color(0xFF2E7D32),
    )

    val catIcon: Map<String, String> = mapOf(
        "خوراک" to "🍔",
        "پوشاک" to "👕",
        "بنزین" to "⛽",
        TRANSPORT to "🚕",
        "قبوض" to "💡",
        "شارژ و اینترنت" to "📱",
        "درمان" to "💊",
        "تفریح" to "🎬",
        "آموزش" to "📚",
        "خانه" to "🏠",
        "بیمه" to "🛡️",
        "خرید اینترنتی" to "🛍️",
        "انتقال وجه" to "🔁",
        "سایر" to "🧾",
        "حقوق" to "💼",
        "درآمد" to "💰",
    )

    fun color(cat: String): Color = catColor[cat] ?: Color(0xFFBDBDBD)
    fun icon(cat: String): String = catIcon[cat] ?: "🧾"
}

fun Typography.withFont(f: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = f),
    displayMedium = displayMedium.copy(fontFamily = f),
    displaySmall = displaySmall.copy(fontFamily = f),
    headlineLarge = headlineLarge.copy(fontFamily = f),
    headlineMedium = headlineMedium.copy(fontFamily = f),
    headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f),
    titleMedium = titleMedium.copy(fontFamily = f),
    titleSmall = titleSmall.copy(fontFamily = f),
    bodyLarge = bodyLarge.copy(fontFamily = f),
    bodyMedium = bodyMedium.copy(fontFamily = f),
    bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f),
    labelMedium = labelMedium.copy(fontFamily = f),
    labelSmall = labelSmall.copy(fontFamily = f),
)
