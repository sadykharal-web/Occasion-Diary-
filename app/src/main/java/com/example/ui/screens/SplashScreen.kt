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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JakartaSansFamily
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

    val entranceScale = remember { Animatable(0.62f) }
    val entranceAlpha = remember { Animatable(0f) }
    val tiltX = remember { Animatable(26f) }
    val tiltY = remember { Animatable(-22f) }
    val shimmerProgress = remember { Animatable(-0.5f) }
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
        launch {
            delay(250L)
            shimmerProgress.animateTo(
                targetValue = 1.6f,
                animationSpec = tween(durationMillis = 1250, easing = FastOutSlowInEasing)
            )
        }
        delay(1950L)
        exitAlpha.animateTo(0f, tween(280, easing = FastOutSlowInEasing))
        onFinished()
    }

    val backgroundBrush = remember {
        Brush.radialGradient(
            colors = listOf(
                Color(0xFF124B3E),
                Color(0xFF0A2C24),
                Color(0xFF051814)
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
                        Color(0x44F2C166),
                        Color(0x181B8A6B),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = Offset(cx, cy)
            )

            // Subtle orbiting golden bokeh accents
            for (i in 0 until 8) {
                val rad = Math.toRadians((ambientAngle + i * 45f).toDouble())
                val orbitX = cx + (size.minDimension * (0.28f + (i % 3) * 0.08f)) * cos(rad).toFloat()
                val orbitY = cy + (size.minDimension * (0.22f + (i % 2) * 0.09f)) * sin(rad).toFloat()
                drawCircle(
                    color = Color(0x55F7D78C),
                    radius = (3.5f + (i % 3) * 2f) * density,
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
                    rotationY = tiltY.value + ambientFloatY * 0.9f
                    translationY = ambientFloatY * density * 0.8f
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Extruded3DTitleLine(
                text = "Family",
                fontSize = 54.sp,
                shimmerFraction = shimmerProgress.value,
                depthSteps = 10,
                stepOffsetX = 1.25.dp,
                stepOffsetY = 1.45.dp
            )

            Spacer(modifier = Modifier.height(2.dp))

            Extruded3DTitleLine(
                text = "Events",
                fontSize = 54.sp,
                shimmerFraction = shimmerProgress.value,
                depthSteps = 10,
                stepOffsetX = 1.25.dp,
                stepOffsetY = 1.45.dp
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Glowing metallic accent bar underneath the 3D title
            Box(
                modifier = Modifier
                    .width(150.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFFD4A24C),
                                Color(0xFFFFF1C9),
                                Color(0xFFD4A24C),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    }
}

@Composable
private fun Extruded3DTitleLine(
    text: String,
    fontSize: TextUnit,
    shimmerFraction: Float,
    depthSteps: Int,
    stepOffsetX: Dp,
    stepOffsetY: Dp
) {
    val depthColors = remember(depthSteps) {
        listOf(
            Color(0xFF281805),
            Color(0xFF3E260A),
            Color(0xFF56350E),
            Color(0xFF6E4513),
            Color(0xFF86571B),
            Color(0xFF9F6B24),
            Color(0xFFB57F2E),
            Color(0xFFC9933B),
            Color(0xFFDAA84D),
            Color(0xFFE9BE67)
        )
    }

    val shimmerStart = (shimmerFraction - 0.35f) * 700f
    val shimmerEnd = (shimmerFraction + 0.35f) * 700f
    val faceBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFFE7BA64),
            Color(0xFFFFF7E2),
            Color(0xFFF6D488),
            Color(0xFFFFFFFF),
            Color(0xFFD89F3F)
        ),
        start = Offset(shimmerStart, 0f),
        end = Offset(shimmerEnd, 240f)
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Soft ambient drop shadow behind extrusion stack
        Text(
            text = text,
            textAlign = TextAlign.Center,
            style = TextStyle(
                fontFamily = JakartaSansFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = fontSize,
                letterSpacing = 1.2.sp,
                color = Color(0x88020B08),
                shadow = Shadow(
                    color = Color(0xAA000000),
                    offset = Offset(6f, 16f),
                    blurRadius = 22f
                )
            ),
            modifier = Modifier.offset(
                x = stepOffsetX * (depthSteps + 2),
                y = stepOffsetY * (depthSteps + 3)
            )
        )

        // Multi-layer 3D depth extrusion stack
        for (layer in depthSteps downTo 1) {
            val colorIdx = ((depthSteps - layer).toFloat() / depthSteps.toFloat() * (depthColors.size - 1))
                .toInt()
                .coerceIn(0, depthColors.lastIndex)
            Text(
                text = text,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    fontFamily = JakartaSansFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = fontSize,
                    letterSpacing = 1.2.sp,
                    color = depthColors[colorIdx]
                ),
                modifier = Modifier.offset(
                    x = stepOffsetX * layer,
                    y = stepOffsetY * layer
                )
            )
        }

        // Front specular metallic face
        Text(
            text = text,
            textAlign = TextAlign.Center,
            style = TextStyle(
                fontFamily = JakartaSansFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = fontSize,
                letterSpacing = 1.2.sp,
                brush = faceBrush,
                shadow = Shadow(
                    color = Color(0x55FFF2C6),
                    offset = Offset(-1f, -1.5f),
                    blurRadius = 4f
                )
            )
        )
    }
}
