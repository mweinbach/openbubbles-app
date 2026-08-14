package app.openbubbles.nativeapp.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** One feature-tour page: icon, title, and a one-line description. */
private data class TourPage(
    val icon: ImageVector,
    val title: String,
    val description: String,
)

private val TourPages = listOf(
    TourPage(
        icon = Icons.AutoMirrored.Filled.Chat,
        title = "Blue-bubble iMessage",
        description = "Sign in with your Apple ID and chat with anyone — texts, tapbacks, and message effects.",
    ),
    TourPage(
        icon = Icons.Filled.PhotoLibrary,
        title = "Photos & attachments",
        description = "Send and receive photos, videos, and files at full quality, straight from your chats.",
    ),
    TourPage(
        icon = Icons.Filled.LocationOn,
        title = "Find My & FaceTime",
        description = "Find My pings and FaceTime call alerts land right here as notifications.",
    ),
)

/**
 * Step 2 — swipeable feature tour. Pages swipe (or step via "Next");
 * "Skip" jumps straight to the sign-in step.
 */
@Composable
internal fun TourStep(
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState { TourPages.size }
    val scope = rememberCoroutineScope()
    val onLastPage = pagerState.currentPage == TourPages.lastIndex

    Column(modifier = modifier.fillMaxSize()) {
        OnboardingTopBar(onBack = onBack, activeSegment = 0)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            TourPageContent(page = TourPages[page], index = page)
        }
        PagerDots(
            count = TourPages.size,
            active = pagerState.currentPage,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 14.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OnboardingPadding)
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onSkip) { Text("Skip") }
            Button(
                onClick = {
                    if (onLastPage) {
                        onContinue()
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
            ) {
                Text(text = if (onLastPage) "Continue" else "Next")
            }
        }
    }
}

@Composable
private fun TourPageContent(page: TourPage, index: Int, modifier: Modifier = Modifier) {
    val (container, content) = tourTone(index)
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(116.dp)
                    .background(container, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = page.icon,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(54.dp),
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(
                text = page.title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = page.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Distinct tonal container per page so the tour feels varied, not templated. */
@Composable
private fun tourTone(index: Int): Pair<Color, Color> = when (index) {
    0 -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    1 -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    else -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
}

/** Animated dot row: the active dot stretches into a pill. */
@Composable
private fun PagerDots(count: Int, active: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val isActive = index == active
            val width by animateDpAsState(
                targetValue = if (isActive) 22.dp else 8.dp,
                label = "dot-width-$index",
            )
            val color by animateColorAsState(
                targetValue = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                label = "dot-color-$index",
            )
            Box(
                Modifier
                    .size(width = width, height = 8.dp)
                    .background(color, RoundedCornerShape(4.dp)),
            )
        }
    }
}
