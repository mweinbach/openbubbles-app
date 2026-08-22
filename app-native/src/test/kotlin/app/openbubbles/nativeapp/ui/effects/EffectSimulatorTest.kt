package app.openbubbles.nativeapp.ui.effects

import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EffectSimulatorTest {

    @Test
    fun `60 hertz advances one fixed step per frame`() {
        val simulator = CountingSimulator()

        repeat(60) { simulator.advance(1f / 60f) }

        assertEquals(60, simulator.steps)
    }

    @Test
    fun `90 hertz fractional frames advance and terminate effects`() {
        assertCompletesAtRefreshRate(90)
    }

    @Test
    fun `120 hertz fractional frames advance and terminate effects`() {
        assertCompletesAtRefreshRate(120)
    }

    @Test
    fun `fractional frame time survives nonpositive frame deltas`() {
        val simulator = CountingSimulator()

        simulator.advance(1f / 120f)
        simulator.advance(-1f)
        simulator.advance(0f)

        assertEquals(0, simulator.steps)
        assertFalse(simulator.isDone())

        simulator.advance(1f / 120f)

        assertEquals(1, simulator.steps)
    }

    @Test
    fun `large frame gaps remain capped to one quarter second`() {
        val cappedFrame = CountingSimulator()
        val oversizedFrame = CountingSimulator()

        cappedFrame.advance(0.25f)
        oversizedFrame.advance(60f)

        assertEquals(cappedFrame.steps, oversizedFrame.steps)
        assertEquals(cappedFrame.time, oversizedFrame.time)
        assertTrue(oversizedFrame.steps <= 15)
    }

    private fun assertCompletesAtRefreshRate(refreshRate: Int) {
        val simulator = CountingSimulator(hardStopSeconds = 5f)

        repeat(refreshRate * 6) {
            if (!simulator.isDone()) simulator.advance(1f / refreshRate)
        }

        assertTrue(simulator.isDone())
        assertTrue(simulator.steps in 300..301)
    }

    private class CountingSimulator(hardStopSeconds: Float = Float.POSITIVE_INFINITY) :
        EffectSimulator(Random(0), hardStopSeconds) {

        var steps = 0
            private set

        override fun step() {
            steps++
        }

        override fun draw(scope: DrawScope) = Unit
    }
}
