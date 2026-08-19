package app.openbubbles.core.text

import kotlin.test.Test
import kotlin.test.assertEquals

class EmoticonReplacementTest {
    @Test
    fun `longer tokens win over shorter prefixes`() {
        assertEquals("🙂", EmoticonReplacement.apply(":-)"))
        assertEquals("hello 🙂", EmoticonReplacement.apply("hello :)"))
    }

    @Test
    fun `heart and cry map`() {
        assertEquals("❤️", EmoticonReplacement.apply("<3"))
        assertEquals("😢", EmoticonReplacement.apply(":'("))
    }

    @Test
    fun `urls are left intact`() {
        val url = "https://example.com/path"
        assertEquals(url, EmoticonReplacement.apply(url))
    }

    @Test
    fun `empty text is unchanged`() {
        assertEquals("", EmoticonReplacement.apply(""))
    }
}
