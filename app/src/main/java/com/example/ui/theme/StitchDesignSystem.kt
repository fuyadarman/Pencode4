package com.example.ui.theme

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Stitch Design System - Psychological & Emotionally Engaging UI/UX.
 * Designed with:
 * - High Cognitive Ease & Hick's Law reduction
 * - Visual Affordance & tactile spring feedback (Fitts' Law)
 * - Dopamine Feedback Loops (luminous gradients, glowing pill badges, smooth depth)
 */
object StitchTheme {
    // Canvas & Surfaces
    val CanvasDark = Color(0xFF0B0F19)
    val SurfaceDark = Color(0xFF131B2B)
    val SurfaceElevated = Color(0xFF1A233A)
    val SurfaceGlass = Color(0xFF162036).copy(alpha = 0.85f)
    val SurfaceCard = Color(0xFF151D2E)

    // Vibrant Radiant Accents
    val PrimaryViolet = Color(0xFF7C3AED)
    val PrimaryIndigo = Color(0xFF6366F1)
    val ElectricCyan = Color(0xFF06B6D4)
    val VibrantSky = Color(0xFF38BDF8)
    val EmeraldSuccess = Color(0xFF10B981)
    val NeonLime = Color(0xFF34D399)
    val SunsetAmber = Color(0xFFF59E0B)
    val RadiantRose = Color(0xFFF43F5E)

    // Borders & Glass
    val BorderSubtle = Color(0xFF24304A)
    val BorderGlow = Color(0xFF3B82F6).copy(alpha = 0.35f)
    val BorderGlass = Color(0xFFFFFFFF).copy(alpha = 0.12f)

    // Text Hierarchy
    val TextMain = Color(0xFFF1F5F9)
    val TextSub = Color(0xFF94A3B8)
    val TextTertiary = Color(0xFF64748B)

    // Gradients
    val AuroraGradient = Brush.horizontalGradient(
        listOf(PrimaryViolet, PrimaryIndigo, ElectricCyan)
    )

    val EmeraldGradient = Brush.horizontalGradient(
        listOf(EmeraldSuccess, NeonLime)
    )

    val FlameGradient = Brush.horizontalGradient(
        listOf(RadiantRose, SunsetAmber)
    )

    val GlassGradient = Brush.verticalGradient(
        listOf(
            Color(0xFF1E293B).copy(alpha = 0.90f),
            Color(0xFF0F172A).copy(alpha = 0.85f)
        )
    )

    val CardMeshBrush = Brush.linearGradient(
        listOf(
            Color(0xFF172033),
            Color(0xFF111827)
        )
    )
}

/**
 * Tactile Spring Scale Effect on Press.
 * Delivers immediate psychological feedback and satisfaction upon user interaction.
 */
@Composable
fun Modifier.stitchPressFeedback(
    scaleDown: Float = 0.94f,
    onClick: (() -> Unit)? = null
): Modifier {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1.0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 500f),
        label = "stitch_press_scale"
    )
    val view = LocalView.current

    return this
        .scale(scale)
        .pointerInput(onClick) {
            awaitPointerEventScope {
                while (true) {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    try {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    } catch (_: Exception) {}
                    val up = waitForUpOrCancellation()
                    isPressed = false
                    if (up != null && onClick != null) {
                        onClick()
                    }
                }
            }
        }
}

/**
 * Psychological High-Affordance Button with luminous gradient,
 * rounded pill contour, and spring feedback.
 */
@Composable
fun StitchPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    gradient: Brush = StitchTheme.AuroraGradient,
    enabled: Boolean = true,
    height: Dp = 48.dp,
    shape: Shape = RoundedCornerShape(16.dp)
) {
    val alpha = if (enabled) 1.0f else 0.45f

    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(gradient)
            .then(
                if (enabled) {
                    Modifier.stitchPressFeedback(scaleDown = 0.96f, onClick = onClick)
                } else Modifier
            )
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = alpha),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = Color.White.copy(alpha = alpha),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp
            )
        }
    }
}

/**
 * Glassmorphic Secondary Button.
 */
@Composable
fun StitchSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    textColor: Color = StitchTheme.TextMain,
    borderColor: Color = StitchTheme.BorderGlass,
    height: Dp = 46.dp,
    shape: Shape = RoundedCornerShape(14.dp)
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(StitchTheme.SurfaceElevated.copy(alpha = 0.75f))
            .border(BorderStroke(1.dp, borderColor), shape)
            .stitchPressFeedback(scaleDown = 0.96f, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = textColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Tactile Floating / Header Icon Action.
 */
@Composable
fun StitchIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = StitchTheme.TextMain,
    background: Color = StitchTheme.SurfaceElevated.copy(alpha = 0.8f),
    size: Dp = 42.dp,
    iconSize: Dp = 20.dp,
    shape: Shape = RoundedCornerShape(12.dp)
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(background)
            .border(BorderStroke(1.dp, StitchTheme.BorderGlass), shape)
            .stitchPressFeedback(scaleDown = 0.92f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Psychological Status Indicator Pill (e.g. AI Active, Live, Syncing, Errors).
 */
@Composable
fun StitchStatusPill(
    label: String,
    modifier: Modifier = Modifier,
    dotColor: Color = StitchTheme.EmeraldSuccess,
    textColor: Color = StitchTheme.TextMain,
    backgroundColor: Color = StitchTheme.SurfaceElevated.copy(alpha = 0.7f),
    onClick: (() -> Unit)? = null
) {
    val clickModifier = if (onClick != null) {
        Modifier.stitchPressFeedback(scaleDown = 0.95f, onClick = onClick)
    } else Modifier

    Surface(
        modifier = modifier
            .clip(CircleShape)
            .border(BorderStroke(1.dp, dotColor.copy(alpha = 0.35f)), CircleShape)
            .then(clickModifier),
        color = backgroundColor,
        shape = CircleShape
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Psychological Glass Card container with soft borders and depth.
 */
@Composable
fun StitchGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    borderColor: Color = StitchTheme.BorderSubtle,
    backgroundBrush: Brush = StitchTheme.CardMeshBrush,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundBrush)
            .border(BorderStroke(1.dp, borderColor), shape)
            .padding(16.dp),
        content = content
    )
}
