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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
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
    onStartFocus: () -> Unit = {},
    onStartRps: () -> Unit = {},
    onSelectRpsChoice: (RpsChoice) -> Unit = {},
    onMemoryClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val target = FaceAnimationDefaults.resolveTarget(
        expression = state.currentExpression,
        isSleeping = state.isSleeping,
        isSpeaking = state.isSpeaking,
        isThinking = state.isThinking,
        isListening = state.isListening,
        isHappyLegacy = state.isHappy
    )

    // Base eye tracking movement (smooth gaze physics)
    val animatedGazeX by animateDpAsState(
        targetValue = if (state.isSleeping) 0.dp
        else if (state.miniGameState.isFollowingFinger) ((state.miniGameState.targetFingerX - 0.5f) * 60f).dp
        else state.gazeX.dp,
        animationSpec = tween(if (state.miniGameState.isFollowingFinger) 120 else 300),
        label = "gazeX"
    )

    val animatedGazeY by animateDpAsState(
        targetValue = if (state.isSleeping) 0.dp
        else if (state.miniGameState.isFollowingFinger) ((state.miniGameState.targetFingerY - 0.5f) * 60f).dp
        else state.gazeY.dp,
        animationSpec = tween(if (state.miniGameState.isFollowingFinger) 120 else 300),
        label = "gazeY"
    )

    // Fluid organic oscillations
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

    val wakeWordPulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wakeWordPulse"
    )

    // Breathing scale for realistic organic presence
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingScale"
    )

    // Dynamic Atmospheric Emotional Aura Glow behind the robot
    val dynamicAuraColor by animateColorAsState(
        targetValue = when {
            state.isSleeping -> Color(0xFF4A3B75).copy(alpha = 0.18f)
            state.pomodoroState.mode == PomodoroMode.FOCUS -> Color(0xFF00ADB5).copy(alpha = 0.22f)
            state.isSpeaking -> Color(0xFFFF9500).copy(alpha = 0.20f)
            state.isListening -> Color(0xFF00C7BE).copy(alpha = 0.28f)
            state.isThinking -> Color(0xFF34C759).copy(alpha = 0.22f)
            (state.currentExpression == FaceExpression.HAPPY || state.currentExpression == FaceExpression.PLAYFUL) ->
                Color(0xFFFF2D55).copy(alpha = 0.18f)
            else -> Color(0xFF007AFF).copy(alpha = 0.14f)
        },
        animationSpec = tween(600),
        label = "dynamicAuraColor"
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
            .background(Color(0xFF000000)) // Pure OLED black for maximum contrast
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTapScreen() },
                    onDoubleTap = { onPet() },
                    onLongPress = { onPet() }
                )
            }
    ) {
        // Stationary subtle edge accent when listening or thinking
        if (state.isListening || state.isThinking) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = 2.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color(0xFF00C7BE).copy(alpha = 0.6f),
                                Color(0xFF007AFF).copy(alpha = 0.6f)
                            )
                        ),
                        shape = androidx.compose.ui.graphics.RectangleShape
                    )
            )
        }

        // Ambient radial backdrop glow centered behind RoboPet
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        dynamicAuraColor,
                        dynamicAuraColor.copy(alpha = dynamicAuraColor.alpha * 0.35f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.minDimension * 0.75f
                )
            )
        }

        // =========================================================================
        // STRICT NON-OVERLAPPING VERTICAL COLUMN (Apple-Grade Hierarchy)
        // =========================================================================
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // -------------------------------------------------------------
            // 1. TOP HEADER BAR (Apple Dynamic Island Style)
            // -------------------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Frosted Relationship Capsule
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x24FFFFFF))
                        .border(0.6.dp, Color(0x38FFFFFF), RoundedCornerShape(20.dp))
                        .clickable { onMemoryClick() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("⭐", fontSize = 11.sp)
                        Text(
                            text = "Lv.${state.relationshipLevel}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.2.sp
                        )
                        if (state.faceDetected) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF34C759))
                            )
                        }
                    }
                }

                // Center: Dynamic Island Pill (Adapts cleanly: Focus / Audio / Time+Battery)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            when {
                                state.pomodoroState.mode == PomodoroMode.FOCUS -> Color(0x3800ADB5)
                                state.isListening -> Color(0x3800C7BE)
                                state.isSpeaking -> Color(0x38FF9500)
                                else -> Color(0x1FFFFFFF)
                            }
                        )
                        .border(
                            0.7.dp,
                            when {
                                state.pomodoroState.mode == PomodoroMode.FOCUS -> Color(0xFF00ADB5).copy(alpha = 0.6f)
                                state.isListening -> Color(0xFF00C7BE).copy(alpha = 0.6f)
                                state.isSpeaking -> Color(0xFFFF9500).copy(alpha = 0.6f)
                                else -> Color(0x2EFFFFFF)
                            },
                            RoundedCornerShape(22.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        when {
                            state.pomodoroState.mode != PomodoroMode.INACTIVE -> {
                                val isFocus = state.pomodoroState.mode == PomodoroMode.FOCUS
                                Text(
                                    text = if (isFocus) "🎯 Focus" else "☕ Break",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = state.pomodoroState.formattedTime,
                                    color = if (isFocus) Color(0xFF86E5FF) else Color(0xFF9CFF9C),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            state.isListening -> {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00C7BE))
                                )
                                Text(
                                    text = "Listening...",
                                    color = Color(0xFF86E5FF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            state.isSpeaking -> {
                                Text(
                                    text = "🔊 Speaking",
                                    color = Color(0xFFFFD60A),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            else -> {
                                val timeDisplay = state.currentTimeString.ifEmpty { "RoboPet" }
                                Text(
                                    text = timeDisplay,
                                    color = Color(0xFFF2F2F7),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = (-0.2).sp
                                )
                                if (state.isCharging) {
                                    Text(
                                        text = "•",
                                        color = Color(0xFF8E8E93),
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        text = "⚡ ${state.batteryLevel}%",
                                        color = Color(0xFF34C759),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else if (state.isWakeWordListening) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .graphicsLayer { alpha = wakeWordPulse }
                                            .clip(CircleShape)
                                            .background(Color(0xFF00C7BE))
                                    )
                                }
                            }
                        }
                    }
                }

                // Right: Studio Settings Glass Button
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0x24FFFFFF))
                        .border(0.6.dp, Color(0x38FFFFFF), CircleShape)
                        .clickable { onMemoryClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⚙️",
                        fontSize = 14.sp
                    )
                }
            }

            // -------------------------------------------------------------
            // 2. CONTEXTUAL SPEECH BUBBLE (Dedicated zone, NEVER overlaps)
            // -------------------------------------------------------------
            AnimatedVisibility(
                visible = !state.spokenText.isNullOrEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xD91C1C1E))
                        .border(0.8.dp, Color(0x38FFFFFF), RoundedCornerShape(22.dp))
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.spokenText ?: "",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        letterSpacing = (-0.2).sp
                    )
                }
            }

            // -------------------------------------------------------------
            // 3. HERO COMPANION FACE (Takes all flexible center space)
            // -------------------------------------------------------------
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.graphicsLayer {
                        alpha = if (state.isNightDimmed) 0.40f else 1.0f
                        scaleX = breathingScale
                        scaleY = breathingScale
                    }
                ) {
                    if (state.isSleeping) {
                        Text(
                            text = "Z z z . . .",
                            color = Color(0xFFE5E5EA).copy(alpha = 0.75f),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 20.dp)
                        )
                    }

                    // Two animated squircle eyes
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(36.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Eye
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AppleRobotEye(
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

                            if (blushAlpha > 0.01f) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Canvas(
                                    modifier = Modifier
                                        .size(40.dp, 18.dp)
                                        .graphicsLayer { alpha = blushAlpha }
                                ) {
                                    drawOval(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                Color(0xFFFF375F).copy(alpha = 0.5f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.height(28.dp))
                            }
                        }

                        // Right Eye
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AppleRobotEye(
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

                            if (blushAlpha > 0.01f) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Canvas(
                                    modifier = Modifier
                                        .size(40.dp, 18.dp)
                                        .graphicsLayer { alpha = blushAlpha }
                                ) {
                                    drawOval(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                Color(0xFFFF375F).copy(alpha = 0.5f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.height(28.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Mouth
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
            }

            // -------------------------------------------------------------
            // 4. INTERACTIVE MINI-GAME CARD (Below face, above dock)
            // -------------------------------------------------------------
            AnimatedVisibility(
                visible = state.miniGameState.isRpsActive,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.94f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xEB1C1C1E))
                        .border(1.dp, Color(0x3DFFFFFF), RoundedCornerShape(24.dp))
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        when (state.miniGameState.rpsPhase) {
                            RpsPhase.COUNTDOWN_3 -> Text("3...", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9500))
                            RpsPhase.COUNTDOWN_2 -> Text("2...", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9500))
                            RpsPhase.COUNTDOWN_1 -> Text("1... Shoot! ✊✋✌️", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34C759))
                            RpsPhase.REVEAL, RpsPhase.FINISHED -> {
                                val robotSymbol = state.miniGameState.robotChoice?.symbol ?: "❓"
                                val userSymbol = state.miniGameState.userChoice?.symbol ?: "🖐️"
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("ROBOPET", fontSize = 10.sp, color = Color(0xFF8E8E93), fontWeight = FontWeight.Bold)
                                        Text(robotSymbol, fontSize = 38.sp)
                                    }
                                    Text("VS", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color(0xFF00C7BE))
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("YOU", fontSize = 10.sp, color = Color(0xFF8E8E93), fontWeight = FontWeight.Bold)
                                        Text(userSymbol, fontSize = 38.sp)
                                    }
                                }
                                if (state.miniGameState.rpsPhase == RpsPhase.FINISHED) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    val resultText = when (state.miniGameState.rpsOutcome) {
                                        RpsOutcome.USER_WINS -> "You Win! 🎉"
                                        RpsOutcome.ROBOT_WINS -> "RoboPet Wins! 🤖"
                                        RpsOutcome.TIE -> "It's a Tie! 🤝"
                                        RpsOutcome.NO_GESTURE_DETECTED -> "Didn't catch hand! 🖐️"
                                        RpsOutcome.NONE -> ""
                                    }
                                    Text(
                                        text = resultText,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (state.miniGameState.rpsOutcome == RpsOutcome.USER_WINS) Color(0xFF34C759) else Color(0xFFFF9500)
                                    )
                                }
                            }
                            RpsPhase.INACTIVE -> {}
                        }

                        // Touch chips: Throw hand OR tap button!
                        if (state.miniGameState.rpsPhase != RpsPhase.FINISHED && state.miniGameState.rpsPhase != RpsPhase.INACTIVE) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Show hand or tap choice:",
                                fontSize = 11.sp,
                                color = Color(0xFF8E8E93),
                                fontWeight = FontWeight.Medium
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(top = 6.dp)
                            ) {
                                RpsChoice.entries.forEach { choice ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color(0x2EFFFFFF))
                                            .border(0.8.dp, Color(0x40FFFFFF), RoundedCornerShape(16.dp))
                                            .clickable { onSelectRpsChoice(choice) }
                                            .padding(horizontal = 14.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(choice.symbol, fontSize = 22.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // 5. BOTTOM ACTION DOCK (Apple VisionOS Glass Capsule)
            // -------------------------------------------------------------
            AppleGlassDock(
                state = state,
                onMicClick = onTapScreen,
                onFocusClick = onStartFocus,
                onPlayClick = onStartRps,
                onHubClick = onMemoryClick
            )
        }
    }
}

/**
 * High-End Apple Squircle OLED Eye with Depth Lens and Specular Glints.
 */
@Composable
fun AppleRobotEye(
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
                // Curved happy squint eye arc with soft glow aura
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val arcPath = Path().apply {
                        moveTo(size.width * 0.12f, size.height * 0.72f)
                        quadraticTo(
                            size.width * 0.5f, size.height * 0.08f,
                            size.width * 0.88f, size.height * 0.72f
                        )
                    }
                    drawPath(
                        arcPath,
                        color = eyeColor.copy(alpha = 0.3f),
                        style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawPath(
                        arcPath,
                        color = eyeColor,
                        style = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            isClosedArc -> {
                // Sleeping downward peaceful curve with soft glow
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val arcPath = Path().apply {
                        moveTo(size.width * 0.15f, size.height * 0.28f)
                        quadraticTo(
                            size.width * 0.5f, size.height * 0.82f,
                            size.width * 0.85f, size.height * 0.28f
                        )
                    }
                    drawPath(
                        arcPath,
                        color = eyeColor.copy(alpha = 0.3f),
                        style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawPath(
                        arcPath,
                        color = eyeColor,
                        style = Stroke(width = 8.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            else -> {
                // Multi-Stop Cyan/Azure Iris Gradient
                val irisBrush = Brush.verticalGradient(
                    colors = listOf(
                        eyeColor,
                        eyeColor.copy(
                            red = (eyeColor.red * 0.80f).coerceIn(0f, 1f),
                            green = (eyeColor.green * 0.85f).coerceIn(0f, 1f),
                            blue = (eyeColor.blue * 0.95f).coerceIn(0f, 1f)
                        )
                    )
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(cornerRadius))
                        .background(irisBrush)
                        .border(1.dp, Color(0x35FFFFFF), RoundedCornerShape(cornerRadius)),
                    contentAlignment = Alignment.Center
                ) {
                    if (pupilSize > 0.dp) {
                        // Deep Midnight Pupil
                        Box(
                            modifier = Modifier
                                .offset(
                                    x = pupilGazeX,
                                    y = 10.dp + pupilGazeY
                                )
                                .size(pupilSize)
                                .clip(CircleShape)
                                .background(pupilColor)
                        ) {
                            // Primary crisp white specular glint (Top-Left)
                            val primarySize = pupilSize * 0.28f
                            Box(
                                modifier = Modifier
                                    .offset(
                                        x = pupilSize * 0.16f,
                                        y = pupilSize * 0.16f
                                    )
                                    .size(primarySize)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.92f))
                            )

                            // Secondary subtle reflection glint (Bottom-Right)
                            val secondarySize = pupilSize * 0.14f
                            Box(
                                modifier = Modifier
                                    .offset(
                                        x = pupilSize * 0.60f,
                                        y = pupilSize * 0.60f
                                    )
                                    .size(secondarySize)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.45f))
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Apple-style Frosted Glass Bottom Action Dock.
 * Features Apple Intelligence Siri Orb centerpiece + quick actions.
 */
@Composable
fun AppleGlassDock(
    state: RobotState,
    onMicClick: () -> Unit,
    onFocusClick: () -> Unit,
    onPlayClick: () -> Unit,
    onHubClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isMicPressed by interactionSource.collectIsPressedAsState()
    val micScale by animateFloatAsState(if (isMicPressed) 0.90f else 1f, tween(100), label = "micScale")

    val infiniteTransition = rememberInfiniteTransition(label = "dockAnim")
    val idleWave by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idleWave"
    )

    val orbScale = if (state.isListening) (1f + (state.audioLevel / 10f).coerceIn(0f, 0.40f)) else idleWave

    // Glassmorphic floating capsule container
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(36.dp))
            .background(Color(0xD91C1C1E))
            .border(1.dp, Color(0x38FFFFFF), RoundedCornerShape(36.dp))
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            // 🎯 Quick Focus / Pomodoro button
            DockIconButton(
                icon = "🎯",
                label = if (state.pomodoroState.mode == PomodoroMode.FOCUS) state.pomodoroState.formattedTime else "Focus",
                isActive = state.pomodoroState.mode == PomodoroMode.FOCUS,
                onClick = onFocusClick
            )

            // 🎮 Quick Rock-Paper-Scissors / Games button
            DockIconButton(
                icon = "✊",
                label = "Play",
                isActive = state.miniGameState.isRpsActive,
                onClick = onPlayClick
            )

            // 🎙️ Apple Intelligence Voice Orb Centerpiece
            Box(
                modifier = Modifier
                    .scale(micScale)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onMicClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Siri Chromatic Aura Ring
                Box(
                    modifier = Modifier
                        .size(66.dp)
                        .scale(orbScale)
                        .clip(CircleShape)
                        .background(
                            Brush.sweepGradient(
                                listOf(
                                    Color(0xFF007AFF).copy(alpha = 0.35f),
                                    Color(0xFF5856D6).copy(alpha = 0.35f),
                                    Color(0xFFFF2D55).copy(alpha = 0.35f),
                                    Color(0xFFFF9500).copy(alpha = 0.35f),
                                    Color(0xFF00C7BE).copy(alpha = 0.35f),
                                    Color(0xFF007AFF).copy(alpha = 0.35f)
                                )
                            )
                        )
                )

                // Siri Glass Orb
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(
                            if (state.isListening) {
                                Brush.linearGradient(listOf(Color(0xFF00C7BE), Color(0xFF007AFF)))
                            } else if (state.isSpeaking) {
                                Brush.linearGradient(listOf(Color(0xFFFF9500), Color(0xFFFF2D55)))
                            } else {
                                Brush.linearGradient(listOf(Color(0xFF2C2C2E), Color(0xFF1C1C1E)))
                            }
                        )
                        .border(1.2.dp, Color(0x60FFFFFF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(22.dp)) {
                        val w = size.width
                        val h = size.height
                        val iconColor = if (state.isListening || state.isSpeaking) Color.White else Color(0xFFE5E5EA)

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
                            style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                        )

                        val stemTop = arcTop + arcHeight
                        val stemBottom = stemTop + h * 0.16f
                        drawLine(
                            color = iconColor,
                            start = Offset(w / 2f, stemTop),
                            end = Offset(w / 2f, stemBottom),
                            strokeWidth = 2.2.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        val baseWidth = w * 0.42f
                        drawLine(
                            color = iconColor,
                            start = Offset((w - baseWidth) / 2f, stemBottom),
                            end = Offset((w + baseWidth) / 2f, stemBottom),
                            strokeWidth = 2.2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            // ✨ Companion Studio & Settings
            DockIconButton(
                icon = "✨",
                label = "Studio",
                isActive = false,
                onClick = onHubClick
            )
        }
    }
}

@Composable
fun DockIconButton(
    icon: String,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.88f else 1f, tween(100), label = "dockBtnScale")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(if (isActive) Color(0x3500C7BE) else Color(0x1AFFFFFF))
                .border(0.8.dp, if (isActive) Color(0xFF00C7BE) else Color(0x28FFFFFF), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = if (isActive) Color(0xFF00C7BE) else Color(0xFF8E8E93),
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium
        )
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
                        .background(
                            Brush.verticalGradient(
                                listOf(color, color.copy(alpha = 0.75f))
                            )
                        )
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
                Box(
                    modifier = Modifier
                        .size(width, height)
                        .clip(RoundedCornerShape(16.dp))
                        .background(color)
                        .padding(3.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Color(0xFF000000))
                )
            }

            MouthType.WAVY -> {
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

            MouthType.NONE -> {}
        }
    }
}
