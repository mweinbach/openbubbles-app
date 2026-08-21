package app.openbubbles.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.uiAutomator
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startupProfile() = rule.collect(
        packageName = TARGET_PACKAGE,
        maxIterations = 15,
        stableIterations = 3,
        includeInStartupProfile = true,
    ) {
        uiAutomator { launch(OpenBubblesJourney.Chats) }
    }

    @Test
    fun criticalUserJourneys() = rule.collect(
        packageName = TARGET_PACKAGE,
        maxIterations = 15,
        stableIterations = 3,
    ) {
        uiAutomator {
            launch(OpenBubblesJourney.Chats)
            scrollCurrentJourney(OpenBubblesJourney.Chats)
            launch(OpenBubblesJourney.Photos)
            scrollCurrentJourney(OpenBubblesJourney.Photos)
        }
    }
}
