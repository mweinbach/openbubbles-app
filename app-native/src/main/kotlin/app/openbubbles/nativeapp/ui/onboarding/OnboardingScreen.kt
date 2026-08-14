package app.openbubbles.nativeapp.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.openbubbles.nativeapp.NativeMainActivity
import app.openbubbles.nativeapp.ui.login.LoginScreen
import app.openbubbles.nativeapp.ui.login.ProvisionScreen
import app.openbubbles.nativeapp.ui.login.RustLoginHandle
import app.openbubbles.nativeapp.ui.login.isProvisioned

/** Horizontal screen padding shared by every onboarding step. */
internal val OnboardingPadding = 24.dp

/** The linear first-run flow: welcome → tour → permissions → connect. */
internal enum class OnboardingStep { Welcome, Tour, Permissions, Connect }

/** The step shown before this one; [OnboardingStep.Welcome] has none. */
internal fun OnboardingStep.previousStep(): OnboardingStep = when (this) {
    OnboardingStep.Welcome -> OnboardingStep.Welcome
    OnboardingStep.Tour -> OnboardingStep.Welcome
    OnboardingStep.Permissions -> OnboardingStep.Tour
    OnboardingStep.Connect -> OnboardingStep.Permissions
}

/**
 * First-run onboarding: welcome → feature tour → permission priming →
 * provisioning → Apple ID sign-in → done. Shown full-screen over the app
 * until the push state is installed.
 *
 * @param onFinished invoked once sign-in succeeded; the host starts the push
 *   service, shows the battery-exemption dialog, and reveals the chat list.
 * @param onLaunchSignIn reserved for the host; intentionally unused here.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    onLaunchSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by remember { mutableStateOf(OnboardingStep.Welcome) }
    var finished by remember { mutableStateOf(false) }

    val appContext = NativeMainActivity.appContext
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
    BackHandler(enabled = step != OnboardingStep.Welcome, onBack = goBack)

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
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val forward = targetState.ordinal >= initialState.ordinal
                    val enter = slideInHorizontally(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ) { full -> if (forward) full / 3 else -full / 3 } + fadeIn(tween(260))
                    val exit = slideOutHorizontally(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ) { full -> if (forward) -full / 3 else full / 3 } + fadeOut(tween(180))
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
                            if (!finished) {
                                finished = true
                                onFinished()
                            }
                        },
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
) {
    Column(modifier = modifier) {
        OnboardingTopBar(onBack = onBack, activeSegment = 2)
        Column(Modifier.padding(horizontal = OnboardingPadding)) {
            Text(
                text = "Connect your Apple ID",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "OpenBubbles signs in to Apple directly from this device. " +
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
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
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
                    CircularProgressIndicator()
                }
                gate == false -> ProvisionScreen(
                    confDir = confDir,
                    onProvisioned = onProvisioned,
                    onBack = onBack,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> LoginScreen(
                    handle = loginHandle ?: RustLoginHandle(path = confDir),
                    onFinished = { _ -> onSignedIn() },
                    onBack = onBack,
                    modifier = Modifier.fillMaxSize(),
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
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Spacer(Modifier.weight(1f))
        StepSegments(active = activeSegment)
        Spacer(Modifier.width(20.dp))
    }
}

/** Three-segment progress row covering tour / permissions / connect. */
@Composable
private fun StepSegments(active: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val isActive = index == active
            val width by animateDpAsState(
                targetValue = if (isActive) 22.dp else 10.dp,
                label = "segment-width-$index",
            )
            val color by animateColorAsState(
                targetValue = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
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
