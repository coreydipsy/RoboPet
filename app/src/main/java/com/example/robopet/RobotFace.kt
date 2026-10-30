package com.example.robopet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
    // Resolve instantaneous target parameters for current expression and states
    val target = FaceAnimationDefaults.resolveTarget(
        expression = state.currentExpression,
        isSleeping = state.isSleeping,
        isSpeaking = state.isSpeaking,
        isThinking = state.isThinking,
        isListening = state.isListening,
        isHappyLegacy = state.isHappy
    )

    // Base eye tracking movement (decoupled from emotional shape)
    val animatedGazeX by animateDpAsState(
        targetValue = if (state.isSleeping) 0.dp else state.gazeX.dp,
        animationSpec = tween(300),
        label = "gazeX"
    )

    val animatedGazeY by animateDpAsState(
        targetValue = if (state.isSleeping) 0.dp else state.gazeY.dp,
        animationSpec = tween(300),
        label = "gazeY"
    )

    // Continuous speaking and listening periodic oscillations
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

    // Sleeping subtle breathing oscillation
    val sleepingBreathScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sleepingBreath"
    )

    // Left eye animated parameters
    val leftEyeWidth by animateDpAsState(target.leftEye.widthDp.dp, tween(280), label = "leftEyeW")
    val leftEyeHeight by animateDpAsState(target.leftEye.heightDp.dp, tween(280), label = "leftEyeH")
    val leftEyeCorner by animateDpAsState(target.leftEye.cornerRadiusDp.dp, tween(280), label = "leftEyeCorner")
    val leftEyeRotation by animateFloatAsState(target.leftEye.rotationDeg, tween(280), label = "leftEyeRot")
    val leftEyeScaleY by animateFloatAsState(
        targetValue = if (state.isBlinking && !state.isSleeping && !target.leftEye.isClosedArc && !target.leftEye.isHappyArc) {
            0.05f
        } else {
            target.leftEye.scaleYMultiplier
        },
        animationSpec = tween(if (state.isBlinking) 100 else 280),
        label = "leftEyeScaleY"
    )
    val leftPupilSize by animateDpAsState(target.leftEye.pupilSizeDp.dp, tween(280), label = "leftPupilSize")
    val leftPupilOffX by animateDpAsState(target.leftEye.pupilOffsetX.dp, tween(280), label = "leftPupilOffX")
    val leftPupilOffY by animateDpAsState(target.leftEye.pupilOffsetY.dp, tween(280), label = "leftPupilOffY")
    val leftEyeColor by animateColorAsState(target.leftEye.eyeColor, tween(300), label = "leftEyeColor")

    // Right eye animated parameters
    val rightEyeWidth by animateDpAsState(target.rightEye.widthDp.dp, tween(280), label = "rightEyeW")
    val rightEyeHeight by animateDpAsState(target.rightEye.heightDp.dp, tween(280), label = "rightEyeH")
    val rightEyeCorner by animateDpAsState(target.rightEye.cornerRadiusDp.dp, tween(280), label = "rightEyeCorner")
    val rightEyeRotation by animateFloatAsState(target.rightEye.rotationDeg, tween(280), label = "rightEyeRot")
    val rightEyeScaleY by animateFloatAsState(
        targetValue = if (state.isBlinking && !state.isSleeping && !target.rightEye.isClosedArc && !target.rightEye.isHappyArc) {
            0.05f
        } else {
            target.rightEye.scaleYMultiplier
        },
        animationSpec = tween(if (state.isBlinking) 100 else 280),
        label = "rightEyeScaleY"
    )
    val rightPupilSize by animateDpAsState(target.rightEye.pupilSizeDp.dp, tween(280), label = "rightPupilSize")
    val rightPupilOffX by animateDpAsState(target.rightEye.pupilOffsetX.dp, tween(280), label = "rightPupilOffX")
    val rightPupilOffY by animateDpAsState(target.rightEye.pupilOffsetY.dp, tween(280), label = "rightPupilOffY")
    val rightEyeColor by animateColorAsState(target.rightEye.eyeColor, tween(300), label = "rightEyeColor")

    // Cheek blush animated parameters
    val blushAlpha by animateFloatAsState(target.blushAlpha, tween(350), label = "blushAlpha")

    // Mouth animated parameters
    val mouthWidth by animateDpAsState(target.mouth.widthDp.dp, tween(280), label = "mouthW")
    val mouthHeight by animateDpAsState(
        targetValue = if (target.mouth.type == MouthType.SPEAKING) talkingMouthHeight.dp else target.mouth.heightDp.dp,
        animationSpec = if (target.mouth.type == MouthType.SPEAKING) tween(60) else tween(280),
        label = "mouthH"
    )
    val mouthRotation by animateFloatAsState(target.mouth.rotationDeg, tween(280), label = "mouthRot")
    val mouthColor by animateColorAsState(target.mouth.color, tween(300), label = "mouthColor")

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
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.graphicsLayer {
                if (state.isSleeping) {
                    scaleX = sleepingBreathScale
                    scaleY = sleepingBreathScale
                }
            }
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

            // Two animated eyes with cheeks
            Row(
                horizontalArrangement = Arrangement.spacedBy(35.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Eye & Cheek
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SingleRobotEye(
                        width = leftEyeWidth,
                        height = leftEyeHeight,
                        cornerRadius = leftEyeCorner,
                        scaleY = leftEyeScaleY,
                        rotation = leftEyeRotation,
                        eyeColor = leftEyeColor,
                        pupilSize = leftPupilSize,
                        pupilGazeX = animatedGazeX + leftPupilOffX,
                        pupilGazeY = animatedGazeY + leftPupilOffY,
                        pupilColor = target.leftEye.pupilColor,
                        isHappyArc = target.leftEye.isHappyArc,
                        isClosedArc = target.leftEye.isClosedArc
                    )

                    // Cheek blush (visible for happy / playful)
                    if (blushAlpha > 0.01f) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .size(24.dp, 8.dp)
                                .graphicsLayer { alpha = blushAlpha }
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFFF85B3).copy(alpha = 0.55f))
                        )
                    } else {
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }

                // Right Eye & Cheek
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SingleRobotEye(
                        width = rightEyeWidth,
                        height = rightEyeHeight,
                        cornerRadius = rightEyeCorner,
                        scaleY = rightEyeScaleY,
                        rotation = rightEyeRotation,
                        eyeColor = rightEyeColor,
                        pupilSize = rightPupilSize,
                        pupilGazeX = animatedGazeX + rightPupilOffX,
                        pupilGazeY = animatedGazeY + rightPupilOffY,
                        pupilColor = target.rightEye.pupilColor,
                        isHappyArc = target.rightEye.isHappyArc,
                        isClosedArc = target.rightEye.isClosedArc
                    )

                    // Cheek blush (visible for happy / playful)
                    if (blushAlpha > 0.01f) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .size(24.dp, 8.dp)
                                .graphicsLayer { alpha = blushAlpha }
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFFF85B3).copy(alpha = 0.55f))
                        )
                    } else {
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Facial expression / Mouth area
            Box(
                modifier = Modifier
                    .height(48.dp)
                    .width(140.dp),
                contentAlignment = Alignment.Center
            ) {
                RobotMouth(
                    type = target.mouth.type,
                    width = mouthWidth,
                    height = mouthHeight,
                    rotation = mouthRotation,
                    color = mouthColor
                )
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
fun SingleRobotEye(
    width: Dp,
    height: Dp,
    cornerRadius: Dp,
    scaleY: Float,
    rotation: Float,
    eyeColor: Color,
    pupilSize: Dp,
    pupilGazeX: Dp,
    pupilGazeY: Dp,
    pupilColor: Color,
    isHappyArc: Boolean,
    isClosedArc: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(width, height)
            .graphicsLayer {
                this.scaleY = scaleY
                rotationZ = rotation
            },
        contentAlignment = Alignment.Center
    ) {
        when {
            isHappyArc -> {
                // Curved happy squint eye arc
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 13.dp.toPx()
                    val arcPath = Path().apply {
                        moveTo(size.width * 0.12f, size.height * 0.75f)
                        quadraticTo(
                            size.width * 0.5f, size.height * 0.08f,
                            size.width * 0.88f, size.height * 0.75f
                        )
                    }
                    drawPath(arcPath, color = eyeColor, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                }
            }

            isClosedArc -> {
                // Sleeping downward peaceful curve
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 10.dp.toPx()
                    val arcPath = Path().apply {
                        moveTo(size.width * 0.15f, size.height * 0.25f)
                        quadraticTo(
                            size.width * 0.5f, size.height * 0.85f,
                            size.width * 0.85f, size.height * 0.25f
                        )
                    }
                    drawPath(arcPath, color = eyeColor, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                }
            }

            else -> {
                // Standard filled rounded eye with pupil inside
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(cornerRadius))
                        .background(eyeColor),
                    contentAlignment = Alignment.Center
                ) {
                    if (pupilSize > 0.dp) {
                        Box(
                            modifier = Modifier
                                .offset(
                                    x = pupilGazeX,
                                    y = 12.dp + pupilGazeY
                                )
                                .size(pupilSize)
                                .clip(CircleShape)
                                .background(pupilColor)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RobotMouth(
    type: MouthType,
    width: Dp,
    height: Dp,
    rotation: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        when (type) {
            MouthType.HEART -> {
                Text(
                    text = "♥",
                    fontSize = 38.sp,
                    color = color,
                    modifier = Modifier.graphicsLayer { rotationZ = rotation }
                )
            }

            MouthType.SPEAKING -> {
                Box(
                    modifier = Modifier
                        .width(width)
                        .height(height)
                        .clip(RoundedCornerShape(12.dp))
                        .background(color)
                )
            }

            MouthType.THINKING -> {
                Text(
                    text = "· · ·",
                    fontSize = 26.sp,
                    color = color,
                    fontWeight = FontWeight.Bold
                )
            }

            MouthType.LISTENING -> {
                Box(
                    modifier = Modifier
                        .size(width)
                        .clip(CircleShape)
                        .background(color)
                )
            }

            MouthType.ROUND_O -> {
                // Surprised tall oval mouth
                Box(
                    modifier = Modifier
                        .size(width, height)
                        .clip(RoundedCornerShape(16.dp))
                        .background(color)
                        .padding(3.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Color(0xFF101018))
                )
            }

            MouthType.WAVY -> {
                // Confused wavy curious line
                Canvas(
                    modifier = Modifier
                        .size(width, height)
                        .graphicsLayer { rotationZ = rotation }
                ) {
                    val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    val path = Path().apply {
                        moveTo(0f, size.height * 0.7f)
                        quadraticTo(
                            size.width * 0.35f, 0f,
                            size.width * 0.65f, size.height * 0.5f
                        )
                        quadraticTo(
                            size.width * 0.85f, size.height,
                            size.width, size.height * 0.3f
                        )
                    }
                    drawPath(path, color = color, style = stroke)
                }
            }

            MouthType.PLAYFUL_SMIRK -> {
                // Playful smirk / cheeky curved mouth
                Canvas(
                    modifier = Modifier
                        .size(width, height)
                        .graphicsLayer { rotationZ = rotation }
                ) {
                    val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    val path = Path().apply {
                        moveTo(size.width * 0.1f, size.height * 0.5f)
                        quadraticTo(
                            size.width * 0.6f, size.height * 0.85f,
                            size.width * 0.95f, size.height * 0.15f
                        )
                    }
                    drawPath(path, color = color, style = stroke)
                }
            }

            MouthType.CURIOUS_DOT -> {
                Box(
                    modifier = Modifier
                        .size(width)
                        .clip(CircleShape)
                        .background(color)
                )
            }

            MouthType.SLEEP_CURVE -> {
                Text(
                    text = "︶",
                    fontSize = 28.sp,
                    color = color,
                    fontWeight = FontWeight.Bold
                )
            }

            MouthType.NONE -> {
                // Blank
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
                    topLeft = androidx.compose.ui.geometry.Offset(bodyLeft, bodyTop),
                    size = androidx.compose.ui.geometry.Size(bodyWidth, bodyHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(bodyWidth / 2f, bodyWidth / 2f)
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
                    topLeft = androidx.compose.ui.geometry.Offset(arcLeft, arcTop),
                    size = androidx.compose.ui.geometry.Size(arcWidth, arcHeight),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )

                // Vertical stem
                val stemTop = arcTop + arcHeight
                val stemBottom = stemTop + h * 0.16f
                drawLine(
                    color = iconColor,
                    start = androidx.compose.ui.geometry.Offset(w / 2f, stemTop),
                    end = androidx.compose.ui.geometry.Offset(w / 2f, stemBottom),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Horizontal base
                val baseWidth = w * 0.42f
                drawLine(
                    color = iconColor,
                    start = androidx.compose.ui.geometry.Offset((w - baseWidth) / 2f, stemBottom),
                    end = androidx.compose.ui.geometry.Offset((w + baseWidth) / 2f, stemBottom),
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
