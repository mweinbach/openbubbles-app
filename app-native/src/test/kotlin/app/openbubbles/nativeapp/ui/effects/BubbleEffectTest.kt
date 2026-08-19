package app.openbubbles.nativeapp.ui.effects

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BubbleEffectTest {
    @Test
    fun `apple ids map to bubble effects`() {
        assertEquals(BubbleEffect.SLAM, BubbleEffect.fromId("com.apple.MobileSMS.expressivesend.slam"))
        assertEquals(BubbleEffect.LOUD, BubbleEffect.fromId("com.apple.MobileSMS.expressivesend.loud"))
        assertEquals(BubbleEffect.GENTLE, BubbleEffect.fromId("com.apple.MobileSMS.expressivesend.gentle"))
        assertEquals(BubbleEffect.ECHO, BubbleEffect.fromId("com.apple.MobileSMS.expressivesend.echo"))
        assertEquals(BubbleEffect.INVISIBLE_INK, BubbleEffect.fromId(INVISIBLE_INK_EFFECT_ID))
    }

    @Test
    fun `picker catalog includes bubble effects`() {
        val ids = SendEffectCatalog.options.map { it.id }.toSet()
        assertEquals(true, BubbleEffect.SLAM.id in ids)
        assertEquals(true, BubbleEffect.LOUD.id in ids)
        assertEquals(true, BubbleEffect.GENTLE.id in ids)
        assertEquals(true, BubbleEffect.ECHO.id in ids)
        assertEquals(true, INVISIBLE_INK_EFFECT_ID in ids)
    }

    @Test
    fun `unknown and screen effects are ignored`() {
        assertNull(BubbleEffect.fromId(null))
        assertNull(BubbleEffect.fromId("com.apple.messages.effect.CKConfettiEffect"))
    }
}
