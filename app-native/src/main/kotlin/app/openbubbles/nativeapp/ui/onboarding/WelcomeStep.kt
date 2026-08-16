package app.openbubbles.nativeapp.ui.onboarding

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.openbubbles.nativeapp.ui.theme.LocalReduceMotion

/** iMessage-blue brand gradient for the welcome bubble (light surfaces). */
private val LightBubbleGradient = Brush.linearGradient(
    listOf(Color(0xFF2B9BFF), Color(0xFF0A57CE)),
)

/** Slightly deeper pair so the bubble keeps its pop on dark surfaces. */
private val DarkBubbleGradient = Brush.linearGradient(
    listOf(Color(0xFF0A84FF), Color(0xFF0148A8)),
)

/**
 * Step 1 — app branding: floating gradient message bubble, name, tagline,
 * "Get Started", and the privacy line. Centered in portrait, scrollable so
 * landscape never cuts the button off.
 */
@Composable
internal fun WelcomeStep(
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OnboardingPadding, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandBubble()
            Spacer(Modifier.height(40.dp))
            Text(
                text = "OpenBubbles",
                // The expressive emphasized display role — a real Medium weight
                // instead of synthetic-bold smear from FontWeight.SemiBold.
                style = MaterialTheme.typography.displaySmallEmphasized,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "iMessage, natively on Android",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(56.dp))
            Button(
                onClick = onGetStarted,
                // Expressive defaults: full-round resting shape with a press
                // morph; the static 18dp override suppressed it.
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(text = "Get Started", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Connects directly to Apple. No middleman servers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Large message-bubble "app icon" built from Compose shapes: concentric
 * tonal halos (an API-level-agnostic glow) behind a rounded gradient tile
 * carrying a white chat glyph. The whole cluster bobs on a slow spring loop.
 */
@Composable
private fun BrandBubble(modifier: Modifier = Modifier) {
    val gradient = if (isSystemInDarkTheme()) DarkBubbleGradient else LightBubbleGradient
    // Ambient loops stop for users who removed animations at the OS level.
    val reduceMotion = LocalReduceMotion.current
    val float = rememberInfiniteTransition(label = "brand-float")
    val bob by if (reduceMotion) {
        remember { mutableStateOf(0f) }
    } else {
        float.animateFloat(
            initialValue = -5f,
            targetValue = 5f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "brand-bob",
        )
    }
    Box(
        modifier = modifier
            .size(250.dp)
            .graphicsLayer { translationY = bob },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), CircleShape),
        )
        Box(
            Modifier
                .fillMaxSize(0.74f)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape),
        )
        Box(
            modifier = Modifier
                .size(148.dp)
                .background(gradient, RoundedCornerShape(40.dp))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.28f),
                    shape = RoundedCornerShape(40.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Chat,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(66.dp),
            )
        }
    }
}
