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
            .background(Color(0xFFF9F2EA))
            .clickable { onDone() },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(R.drawable.splash_logo),
            contentDescription = "هومایه",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .alpha(a.value)
                .scale(0.92f + 0.08f * a.value),
        )
    }
}
