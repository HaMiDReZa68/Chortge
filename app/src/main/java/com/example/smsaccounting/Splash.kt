package com.example.smsaccounting

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** صفحهٔ شروع: آرم هومایه ظاهر می‌شود و خودکار وارد برنامه می‌شویم (با لمس هم می‌شود رد کرد) */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        a.animateTo(1f, tween(1000))
        delay(1000)
        onDone()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Teal1)
            .clickable { onDone() },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(420.dp).alpha(0.5f * a.value).clip(CircleShape).border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape))
        Box(Modifier.size(290.dp).alpha(0.8f * a.value).clip(CircleShape).border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape))
        Column(
            Modifier
                .alpha(a.value)
                .scale(0.9f + 0.1f * a.value),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painterResource(R.drawable.logo_mask),
                contentDescription = "هومایه",
                colorFilter = ColorFilter.tint(Color.White),
                contentScale = ContentScale.Fit,
                modifier = Modifier.width(240.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text("حساب‌وکتاب هوشمند پیامک", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
        }
    }
}
