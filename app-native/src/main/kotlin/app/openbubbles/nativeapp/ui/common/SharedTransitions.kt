package app.openbubbles.nativeapp.ui.common

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation3.ui.LocalNavAnimatedContentScope

/**
 * True while the list-detail scene renders more than one pane. Shared-element
 * transitions between list and detail must not run when both ends of the
 * "transition" are simultaneously visible — the keys would collide.
 */
val LocalIsMultiPane = compositionLocalOf { false }

/**
 * The SharedTransitionLayout wrapping the root NavDisplay, provided manually
 * because compose-animation no longer ships a LocalSharedTransitionScope.
 * Null in previews and tests, where the helpers below no-op.
 */
val LocalAppSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/**
 * Container transform between a chat row and its conversation screen.
 * No-ops outside a SharedTransitionLayout (previews, tests) and in
 * multi-pane layouts where list and detail are visible together.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedChatContainer(chatId: Long): Modifier {
    if (LocalIsMultiPane.current) return this
    val sharedScope = LocalAppSharedTransitionScope.current ?: return this
    // Only reachable inside a NavDisplay entry (guaranteed by the scope above).
    val navScope = LocalNavAnimatedContentScope.current
    return with(sharedScope) {
        this@sharedChatContainer.sharedBounds(
            sharedContentState = rememberSharedContentState(key = "chat-container-$chatId"),
            animatedVisibilityScope = navScope,
        )
    }
}

/**
 * Shared-element continuity between an attachment thumbnail and the
 * fullscreen viewer. The viewer is always a full-screen destination, so the
 * thumbnail and the viewer are never visible at once — no pane guard needed.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedAttachment(guid: String): Modifier {
    val sharedScope = LocalAppSharedTransitionScope.current ?: return this
    val navScope = LocalNavAnimatedContentScope.current
    return with(sharedScope) {
        this@sharedAttachment.sharedElement(
            sharedContentState = rememberSharedContentState(key = "attachment-$guid"),
            animatedVisibilityScope = navScope,
        )
    }
}

