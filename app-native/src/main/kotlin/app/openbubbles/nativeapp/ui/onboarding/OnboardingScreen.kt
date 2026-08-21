package app.openbubbles.nativeapp.ui.onboarding

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import kotlin.coroutines.cancellation.CancellationException
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import app.openbubbles.nativeapp.data.AppContext
import app.openbubbles.nativeapp.data.InitialHistoryDownload
import app.openbubbles.nativeapp.ui.login.LoginScreen
import app.openbubbles.nativeapp.ui.login.ProvisionScreen
import app.openbubbles.nativeapp.ui.login.RustLoginHandle
import app.openbubbles.nativeapp.ui.login.isProvisioned
import app.openbubbles.nativeapp.ui.NavTransitions
import app.openbubbles.nativeapp.ui.theme.LocalReduceMotion
import app.openbubbles.nativeapp.ui.theme.defaultEffectsSpec
import app.openbubbles.nativeapp.ui.theme.defaultSpatialSpec
import app.openbubbles.nativeapp.ui.theme.fastEffectsSpec
import app.openbubbles.nativeapp.ui.theme.fastSpatialSpec
import app.openbubbles.nativeapp.ui.theme.slowSpatialSpec

/** Horizontal screen padding shared by every onboarding step. */
internal val OnboardingPadding = 24.dp

/**
 * The linear first-run flow: welcome → tour → permissions → connect →
 * iCloud unlock → history download choice.
 */
internal enum class OnboardingStep { Welcome, Tour, Permissions, Connect, Keychain, History }

/** The step shown before this one; [OnboardingStep.Welcome] has none. */
internal fun OnboardingStep.previousStep(): OnboardingStep = when (this) {
    OnboardingStep.Welcome -> OnboardingStep.Welcome
    OnboardingStep.Tour -> OnboardingStep.Welcome
    OnboardingStep.Permissions -> OnboardingStep.Tour
    OnboardingStep.Connect -> OnboardingStep.Permissions
    // Sign-in already happened by the time these render; going back to it
    // would re-run an Apple activation, so they only step forward.
    OnboardingStep.Keychain -> OnboardingStep.Keychain
    OnboardingStep.History -> OnboardingStep.Keychain
}

/** Steps the user can still walk backwards out of. */
internal fun OnboardingStep.canGoBack(): Boolean = previousStep() != this

/**
 * First-run onboarding: welcome → feature tour → permission priming →
 * provisioning → Apple ID sign-in → iCloud Keychain unlock → history
 * download choice. Shown full-screen over the app until it completes.
 *
 * @param onSignedIn invoked the moment Apple sign-in succeeds, before the
 *   remaining steps run: the host starts the push service so the keychain and
 *   history steps have a live connection to work with, and latches this
 *   screen so the arriving push state does not tear it down.
 * @param onFinished invoked when the last step is done; the host persists
 *   completion and reveals the app.
 */
@Composable
fun OnboardingScreen(
    onSignedIn: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by remember { mutableStateOf(OnboardingStep.Welcome) }
    var signedIn by remember { mutableStateOf(false) }
    var keychainUnlocked by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    val appContext = AppContext.current
    val confDir = remember(appContext) { appContext?.filesDir?.absolutePath.orEmpty() }
    val loginHandle = remember(confDir) {
        confDir.takeIf { it.isNotBlank() }?.let { RustLoginHandle(path = it) }
    }

    // Provisioning gate, preloaded so the connect step opens without a wait.
    var provisioned by remember(confDir) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(confDir) {
        if (confDir.isNotBlank()) provisioned = isProvisioned(confDir)
    }

    val goBack: () -> Unit = { step = step.previousStep() }
    val finish: () -> Unit = {
        if (!finished) {
            finished = true
            onFinished()
        }
    }
    var backProgress by remember { mutableFloatStateOf(0f) }
    var backEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    PredictiveBackHandler(enabled = step.canGoBack()) { events ->
        try {
            events.collect { event ->
                backProgress = event.progress
                backEdge = event.swipeEdge
            }
            backProgress = 0f
            goBack()
        } catch (_: CancellationException) {
            backProgress = 0f
        }
    }

    // Full-screen step changes ride the theme's motion scheme (Slow spatial
    // tier) instead of hand-rolled springs; reduced-motion users get snaps.
    val stepEnterSpatial = slowSpatialSpec<IntOffset>()
    val stepExitSpatial = fastSpatialSpec<IntOffset>()
    val stepFadeIn = defaultEffectsSpec<Float>()
    val stepFadeOut = fastEffectsSpec<Float>()
    val reduceMotion = LocalReduceMotion.current
    val previewProgress by animateFloatAsState(
        targetValue = backProgress,
        animationSpec = if (backProgress == 0f) defaultSpatialSpec() else snap(),
        label = "onboarding-back",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            AnimatedContent(
                targetState = step,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (reduceMotion || previewProgress == 0f) return@graphicsLayer
                        transformOrigin = NavTransitions.predictivePopOrigin(backEdge)
                        val scale = 1f - (1f - NavTransitions.PREDICTIVE_SCALE) * previewProgress
                        scaleX = scale
                        scaleY = scale
                        val shift = 48.dp.toPx() * previewProgress
                        translationX = if (backEdge == BackEventCompat.EDGE_LEFT) shift else -shift
                        alpha = 1f - 0.12f * previewProgress
                    },
                transitionSpec = {
                    val forward = targetState.ordinal >= initialState.ordinal
                    val enter = slideInHorizontally(
                        animationSpec = stepEnterSpatial,
                    ) { full -> if (forward) full / 3 else -full / 3 } + fadeIn(stepFadeIn)
                    val exit = slideOutHorizontally(
                        animationSpec = stepExitSpatial,
                    ) { full -> if (forward) -full / 3 else full / 3 } + fadeOut(stepFadeOut)
                    enter togetherWith exit
                },
                label = "onboarding-step",
            ) { current ->
                when (current) {
                    OnboardingStep.Welcome -> WelcomeStep(
                        onGetStarted = { step = OnboardingStep.Tour },
                        modifier = Modifier.fillMaxSize(),
                    )
                    OnboardingStep.Tour -> TourStep(
                        onContinue = { step = OnboardingStep.Permissions },
                        onSkip = { step = OnboardingStep.Connect },
                        onBack = goBack,
                        modifier = Modifier.fillMaxSize(),
                    )
                    OnboardingStep.Permissions -> PermissionsStep(
                        onContinue = { step = OnboardingStep.Connect },
                        onBack = goBack,
                        modifier = Modifier.fillMaxSize(),
                    )
                    OnboardingStep.Connect -> ConnectStep(
                        confDir = confDir,
                        loginHandle = loginHandle,
                        provisioned = provisioned,
                        onProvisioned = { provisioned = true },
                        onBack = goBack,
                        onSignedIn = {
                            if (!signedIn) {
                                signedIn = true
                                onSignedIn()
                            }
                            step = OnboardingStep.Keychain
                        },
                        modifier = Modifier.fillMaxSize(),
                        onRedoSetup = { provisioned = false },
                    )
                    OnboardingStep.Keychain -> KeychainStep(
                        onContinue = { joined ->
                            keychainUnlocked = joined
                            step = OnboardingStep.History
                        },
                        onBack = goBack,
                        modifier = Modifier.fillMaxSize(),
                    )
                    OnboardingStep.History -> HistoryStep(
                        canDownload = keychainUnlocked,
                        onStartDownload = {
                            appContext?.let(InitialHistoryDownload::arm)
                            finish()
                        },
                        onSkip = finish,
                        onBack = goBack,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ connect

/**
 * Final step: hardware provisioning (only the first time) followed by the
 * embedded Apple ID sign-in. The embedded screens fill the space below the
 * header and carry their own progress UI.
 */
@Composable
private fun ConnectStep(
    confDir: String,
    loginHandle: RustLoginHandle?,
    provisioned: Boolean?,
    onProvisioned: () -> Unit,
    onBack: () -> Unit,
    onSignedIn: () -> Unit,
    modifier: Modifier = Modifier,
    onRedoSetup: () -> Unit = {},
) {
    val gateFadeIn = defaultEffectsSpec<Float>()
    val gateFadeOut = fastEffectsSpec<Float>()
    Column(modifier = modifier) {
        OnboardingTopBar(onBack = onBack, activeSegment = 2)
        Column(Modifier.padding(horizontal = OnboardingPadding)) {
            Text(
                text = "Connect your Apple ID",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "OpenGarden signs in to Apple directly from this device. " +
                    "Your credentials stay here — nothing passes through other servers.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }
        AnimatedContent(
            targetState = provisioned,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            transitionSpec = {
                fadeIn(gateFadeIn) togetherWith fadeOut(gateFadeOut)
            },
            label = "provision-gate",
        ) { gate ->
            when {
                confDir.isBlank() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Storage unavailable — restart the app and try again.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(OnboardingPadding),
                    )
                }
                gate == null -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }
                gate == false -> ProvisionScreen(
                    confDir = confDir,
                    onProvisioned = onProvisioned,
                    onBack = onBack,
                    modifier = Modifier.fillMaxSize(),
                    showBackAction = false,
                )
                else -> LoginScreen(
                    handle = loginHandle ?: RustLoginHandle(path = confDir),
                    onFinished = { _ -> onSignedIn() },
                    onBack = onBack,
                    modifier = Modifier.fillMaxSize(),
                    onRedoSetup = onRedoSetup,
                    embedded = true,
                )
            }
        }
    }
}

// ------------------------------------------------------------ shared chrome

/** Slim step top bar: back arrow on the left, progress segments on the right. */
@Composable
internal fun OnboardingTopBar(
    onBack: () -> Unit,
    activeSegment: Int,
    modifier: Modifier = Modifier,
    showBack: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        } else {
            Spacer(Modifier.width(OnboardingPadding))
        }
        Spacer(Modifier.weight(1f))
        StepSegments(active = activeSegment)
        Spacer(Modifier.width(20.dp))
    }
}

/** Progress row covering tour / permissions / connect / unlock / history. */
private const val ONBOARDING_SEGMENTS = 5

@Composable
private fun StepSegments(active: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(ONBOARDING_SEGMENTS) { index ->
            val isActive = index == active
            val width by animateDpAsState(
                targetValue = if (isActive) 22.dp else 10.dp,
                animationSpec = fastSpatialSpec(),
                label = "segment-width-$index",
            )
            val color by animateColorAsState(
                targetValue = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                animationSpec = fastEffectsSpec(),
                label = "segment-color-$index",
            )
            Box(
                Modifier
                    .size(width = width, height = 4.dp)
                    .background(color, RoundedCornerShape(2.dp)),
            )
        }
    }
}
