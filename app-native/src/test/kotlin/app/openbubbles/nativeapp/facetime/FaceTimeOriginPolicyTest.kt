package app.openbubbles.nativeapp.facetime

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FaceTimeOriginPolicyTest {

    @Test
    fun `FaceTime WebView requires HTTPS and exact origin`() {
        val origin = assertNotNull(secureWebOrigin("https://facetime.example.test/join/abc"))

        assertTrue(matchesWebOrigin(origin, "https://facetime.example.test/room"))
        assertFalse(matchesWebOrigin(origin, "https://attacker.example.test/room"))
        assertFalse(matchesWebOrigin(origin, "http://facetime.example.test/room"))
        assertNull(secureWebOrigin("javascript:alert(1)"))
    }
}
