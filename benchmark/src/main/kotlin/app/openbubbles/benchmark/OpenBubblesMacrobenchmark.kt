package app.openbubbles.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.uiAutomator
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class OpenBubblesMacrobenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartupWithoutCompilation() = startup(CompilationMode.None())

    @Test
    fun coldStartupWithBaselineProfile() = startup(
        CompilationMode.Partial(BaselineProfileMode.Require),
    )

    @Test
    fun chatListScroll() = scroll(OpenBubblesJourney.Chats)

    @Test
    fun photosScroll() = scroll(OpenBubblesJourney.Photos)

    private fun startup(compilationMode: CompilationMode) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilationMode,
        startupMode = StartupMode.COLD,
        iterations = 8,
        setupBlock = { pressHome() },
    ) {
        uiAutomator { launch(OpenBubblesJourney.Chats) }
    }

    private fun scroll(journey: OpenBubblesJourney) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        startupMode = StartupMode.WARM,
        iterations = 8,
        setupBlock = {
            uiAutomator { launch(journey) }
        },
    ) {
        uiAutomator { scrollCurrentJourney(journey) }
    }
}
