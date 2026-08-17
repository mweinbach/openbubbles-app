package app.openbubbles.nativeapp.ui

import androidx.compose.ui.graphics.TransformOrigin
import androidx.navigationevent.NavigationEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class NavTransitionsTest {

    @Test
    fun `predictive pop origin pivots away from the swipe edge`() {
        val fromLeft = NavTransitions.predictivePopOrigin(NavigationEvent.EDGE_LEFT)
        val fromRight = NavTransitions.predictivePopOrigin(NavigationEvent.EDGE_RIGHT)
        val none = NavTransitions.predictivePopOrigin(NavigationEvent.EDGE_NONE)

        assertEquals(TransformOrigin(1f - NavTransitions.PIVOT_FRACTION, 0.5f), fromLeft)
        assertEquals(TransformOrigin(NavTransitions.PIVOT_FRACTION, 0.5f), fromRight)
        assertEquals(TransformOrigin.Center, none)
        assertNotEquals(fromLeft, fromRight)
    }
}
