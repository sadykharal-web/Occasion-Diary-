package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current.density

    val entranceScale = remember { Animatable(0.58f) }
    val entranceAlpha = remember { Animatable(0f) }
    val tiltX = remember { Animatable(24f) }
    val tiltY = remember { Animatable(-20f) }
    val exitAlpha = remember { Animatable(1f) }

    val infiniteTransition = rememberInfiniteTransition(label = "splash_ambient")
    val ambientAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ambient_angle"
    )
    val ambientFloatY by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient_float"
    )

    LaunchedEffect(Unit) {
        launch {
            entranceAlpha.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
        }
        launch {
            entranceScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        launch {
            tiltX.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        launch {
            tiltY.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessVeryLow
                )
            )
        }
        delay(1950L)
        exitAlpha.animateTo(0f, tween(280, easing = FastOutSlowInEasing))
        onFinished()
    }

    val backgroundBrush = remember {
        Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFFFFF),
                Color(0xFFFFF8E8),
                Color(0xFFF3E6C9)
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .alpha(exitAlpha.value)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onFinished
            )
            .testTag("splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val glowRadius = size.minDimension * 0.48f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x55F4C860),
                        Color(0x22E5A93B),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = Offset(cx, cy)
            )

            for (i in 0 until 8) {
                val rad = Math.toRadians((ambientAngle + i * 45f).toDouble())
                val orbitX = cx + (size.minDimension * (0.30f + (i % 3) * 0.07f)) * cos(rad).toFloat()
                val orbitY = cy + (size.minDimension * (0.24f + (i % 2) * 0.08f)) * sin(rad).toFloat()
                drawCircle(
                    color = Color(0x66D89B2B),
                    radius = (3.5f + (i % 3) * 1.8f) * density,
                    center = Offset(orbitX, orbitY)
                )
            }
        }

        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp)
                .graphicsLayer {
                    cameraDistance = 18f * density
                    scaleX = entranceScale.value
                    scaleY = entranceScale.value
                    alpha = entranceAlpha.value
                    rotationX = tiltX.value
                    rotationY = tiltY.value + ambientFloatY * 0.8f
                    translationY = ambientFloatY * density * 0.8f
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.family_events_logo),
                contentDescription = "Family Events",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .sizeIn(maxWidth = 340.dp, maxHeight = 340.dp)
                    .testTag("splash_logo_image")
            )

            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .width(150.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFFD49B35),
                                Color(0xFFFFD86B),
                                Color(0xFFD49B35),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    }
}
