package app.openbubbles.nativeapp.ui.chatlist

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatListLayoutTest {

    @Test
    fun `pinned columns follow GridCells Adaptive math`() {
        assertEquals(3, pinnedChatColumnCount(395.dp))
        assertEquals(4, pinnedChatColumnCount(560.dp))
        assertEquals(6, pinnedChatColumnCount(760.dp))
        assertEquals(1, pinnedChatColumnCount(80.dp))
        assertEquals(8, pinnedChatColumnCount(960.dp))
    }
}
