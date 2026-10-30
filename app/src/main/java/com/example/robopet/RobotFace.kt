package com.example.robopet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RobotFace(
    state: RobotState,
    onPet: () -> Unit,
    onTapScreen: () -> Unit,
    onMemoryClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Smooth blinking animation (disabled when sleeping)
    val eyeScale by animateFloatAsState(
        targetValue = when {
            state.isSleeping -> 0.08f
            state.isBlinking -> 0.05f
            else -> 1f
        },
        animationSpec = tween(120),
        label = "blink"
    )

    // Smooth eye movements
    val animatedX by animateDpAsState(
        targetValue = if (state.isSleeping) 0.dp else state.gazeX.dp,
        animationSpec = tween(350),
        label = "gazeX"
    )

    val animatedY by animateDpAsState(
        targetValue = if (state.isSleeping) 0.dp else state.gazeY.dp,
        animationSpec = tween(350),
        label = "gazeY"
    )

    // Speaking mouth animation
    val infiniteTransition = rememberInfiniteTransition(label = "robotExpressions")
    val talkingMouthHeight by infiniteTransition.animateFloat(
        initialValue = 6f,
        targetValue = 24f,
        animationSpec = infiniteRepeatable(
            animation = tween(180, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "talkingMouth"
    )

    val listeningPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "listeningPulse"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF101018))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTapScreen() },
                    onDoubleTap = { onPet() },
                    onLongPress = { onPet() }
                )
            },
        contentAlignment = Alignment.Center
    ) {

        // Subtle Listening Indicator at the top
        ListeningIndicator(
            isListening = state.isListening,
            audioLevel = state.audioLevel,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp)
        )

        // Memory Debug Screen button at top-right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 40.dp, end = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E2638).copy(alpha = 0.85f))
                .border(1.dp, Color(0xFF86E5FF).copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .clickable { onMemoryClick() }
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = "🧠 Memory",
                color = Color(0xFF86E5FF),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Spoken text speech bubble
        AnimatedVisibility(
            visible = !state.spokenText.isNullOrEmpty(),
            enter = fadeIn() + slideInVertically(initialOffsetY = { 20 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -20 }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 130.dp, start = 32.dp, end = 32.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF1E2235))
                    .border(1.dp, Color(0xFF86E5FF).copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = state.spokenText ?: "",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Main Robot Face column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Sleeping Zzz floating animation
            if (state.isSleeping) {
                Text(
                    text = "Z z z . . .",
                    color = Color(0xFF86E5FF).copy(alpha = 0.8f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // Draw two eyes
            Row(
                horizontalArrangement = Arrangement.spacedBy(35.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(2) {
                    Box(
                        modifier = Modifier
                            .size(95.dp, 125.dp)
                            .graphicsLayer {
                                scaleY = eyeScale
                            }
                            .background(
                                if (state.isSleeping) Color(0xFF4A7B9D) else Color(0xFF86E5FF),
                                RoundedCornerShape(48.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        // Pupil (visible only when awake)
                        if (!state.isSleeping) {
                            Box(
                                modifier = Modifier
                                    .offset(
                                        x = animatedX,
                                        y = 12.dp + animatedY
                                    )
                                    .size(28.dp)
                                    .background(
                                        Color(0xFF101018),
                                        CircleShape
                                    )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Facial expression / Mouth area
            Box(
                modifier = Modifier
                    .height(48.dp)
                    .width(140.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    state.isHappy -> {
                        Text(
                            text = "♥",
                            fontSize = 38.sp,
                            color = Color(0xFFFF85B3)
                        )
                    }
                    state.isSpeaking -> {
                        // Animated talking mouth synchronized with speech
                        Box(
                            modifier = Modifier
                                .width(38.dp)
                                .height(talkingMouthHeight.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF86E5FF))
                        )
                    }
                    state.isSleeping -> {
                        Text(
                            text = "︶",
                            fontSize = 28.sp,
                            color = Color(0xFF86E5FF).copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    state.isThinking -> {
                        Text(
                            text = "· · ·",
                            fontSize = 26.sp,
                            color = Color(0xFF86E5FF),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    state.isListening -> {
                        // Subtle cute curious mouth while listening
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF86E5FF).copy(alpha = 0.8f))
                        )
                    }
                }
            }
        }

        // Interactive Recording Indicator / Tap Hint at bottom center
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) {
            val hintText = when {
                state.isThinking -> "Thinking... 🤔"
                state.isListening -> "Listening... (tap to stop)"
                state.isSpeaking -> "Speaking... (tap to interrupt)"
                else -> "Tap anywhere to talk"
            }
            Text(
                text = hintText,
                color = when {
                    state.isThinking -> Color(0xFF9CFF9C)
                    state.isListening -> Color(0xFF00E5FF)
                    state.isSpeaking -> Color(0xFFFFC080)
                    else -> Color(0xFF86E5FF).copy(alpha = 0.75f)
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            MicrophoneButton(
                isListening = state.isListening,
                isSpeaking = state.isSpeaking,
                listeningPulse = listeningPulse,
                onClick = onTapScreen
            )
        }

        // Status / Error message toast near bottom
        state.speechError?.let { error ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF2A1C24))
                    .border(1.dp, Color(0xFFFF6B6B).copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = error,
                    color = Color(0xFFFF9999),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun ListeningIndicator(
    isListening: Boolean,
    audioLevel: Float,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isListening,
        enter = fadeIn() + slideInVertically(initialOffsetY = { -20 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { -20 }),
        modifier = modifier
    ) {
        val clampedAudio = (audioLevel / 10f).coerceIn(0.2f, 1.5f)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF15222E))
                .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Pulsing dot reacting to audio input
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .scale(clampedAudio)
                    .clip(CircleShape)
                    .background(Color(0xFF00E5FF))
            )
            Text(
                text = "Listening...",
                color = Color(0xFF86E5FF),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun MicrophoneButton(
    isListening: Boolean,
    isSpeaking: Boolean,
    listeningPulse: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val buttonColor = when {
        isSpeaking -> Color(0xFF2A2A3A) // Dimmed when robot is speaking (prevent self-recognition)
        isListening -> Color(0xFF00BCD4)
        else -> Color(0xFF23283E)
    }

    val iconColor = when {
        isSpeaking -> Color(0xFF666677)
        isListening -> Color(0xFF101018)
        else -> Color(0xFF86E5FF)
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Outer pulsing ring when listening
        if (isListening) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .scale(listeningPulse)
                    .clip(CircleShape)
                    .background(Color(0xFF00E5FF).copy(alpha = 0.25f))
            )
        }

        // Main button
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(buttonColor)
                .border(
                    width = 2.dp,
                    color = if (isListening) Color(0xFF86E5FF) else Color(0xFF3B4466),
                    shape = CircleShape
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            // Sleek Canvas microphone icon
            Canvas(modifier = Modifier.size(24.dp)) {
                val w = size.width
                val h = size.height

                // Capsule body of microphone
                val bodyWidth = w * 0.38f
                val bodyHeight = h * 0.52f
                val bodyLeft = (w - bodyWidth) / 2f
                val bodyTop = h * 0.12f

                drawRoundRect(
                    color = iconColor,
                    topLeft = Offset(bodyLeft, bodyTop),
                    size = Size(bodyWidth, bodyHeight),
                    cornerRadius = CornerRadius(bodyWidth / 2f, bodyWidth / 2f)
                )

                // Arc cradle around capsule
                val arcWidth = w * 0.62f
                val arcHeight = h * 0.44f
                val arcLeft = (w - arcWidth) / 2f
                val arcTop = h * 0.28f

                drawArc(
                    color = iconColor,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(arcLeft, arcTop),
                    size = Size(arcWidth, arcHeight),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )

                // Vertical stem
                val stemTop = arcTop + arcHeight
                val stemBottom = stemTop + h * 0.16f
                drawLine(
                    color = iconColor,
                    start = Offset(w / 2f, stemTop),
                    end = Offset(w / 2f, stemBottom),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Horizontal base
                val baseWidth = w * 0.42f
                drawLine(
                    color = iconColor,
                    start = Offset((w - baseWidth) / 2f, stemBottom),
                    end = Offset((w + baseWidth) / 2f, stemBottom),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
fun FaceDebugIndicator(
    detected: Boolean,
    speechNotice: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (detected) "FACE DETECTED" else "NO FACE",
            color = if (detected) Color(0xFF9CFF9C) else Color(0xFFFFC080),
            fontSize = 12.sp
        )
        if (!speechNotice.isNullOrEmpty()) {
            Text(
                text = speechNotice,
                color = Color(0xFF86E5FF).copy(alpha = 0.7f),
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
