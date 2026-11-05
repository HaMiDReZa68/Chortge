package com.example.smsaccounting

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val Nastaliq = FontFamily(Font(R.font.nastaliq))

/** صفحهٔ شروع: نام «هومایه» با نستعلیق ظاهر می‌شود و خودکار وارد برنامه می‌شویم (با لمس هم می‌شود رد کرد) */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        a.animateTo(1f, tween(1100))
        delay(1100)
        onDone()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Teal1, Teal2)))
            .clickable { onDone() },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(320.dp)
                .alpha(0.10f * a.value)
                .clip(CircleShape)
                .background(Color.White)
        )
        Box(
            Modifier
                .size(210.dp)
                .alpha(0.10f * a.value)
                .clip(CircleShape)
                .background(Color.White)
        )
        Column(
            Modifier
                .alpha(a.value)
                .scale(0.85f + 0.15f * a.value),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "هومایه",
                color = Color.White,
                fontFamily = Nastaliq,
                style = TextStyle(fontSize = 84.sp, lineHeight = 150.sp, textAlign = TextAlign.Center),
                modifier = Modifier.padding(vertical = 16.dp),
            )
            Text(
                "حساب‌وکتاب هوشمند پیامک",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp,
            )
        }
    }
}
