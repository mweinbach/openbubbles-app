package app.openbubbles.core

import app.openbubbles.core.model.InteractivePayload
import app.openbubbles.core.model.InteractivePayloadParser
import app.openbubbles.core.model.MessageMapper
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import uniffi.rust_lib_bluebubbles.UConversation
import uniffi.rust_lib_bluebubbles.UIndexedPart
import uniffi.rust_lib_bluebubbles.UMessage
import uniffi.rust_lib_bluebubbles.UMessageInst
import uniffi.rust_lib_bluebubbles.UPart

class InteractivePayloadTest {
    @Test
    fun `poll data url maps question options and vote counts`() {
        val pollJson = """{"item":{"title":"Lunch?","orderedPollOptions":[{"optionIdentifier":"a","text":"Tacos"},{"optionIdentifier":"b","text":"Pizza"}],"votes":[{"voteOptionIdentifier":"a","participantHandle":"mailto:a@example.com"},{"voteOptionIdentifier":"a","participantHandle":"mailto:b@example.com"}]},"version":1}"""
        val encoded = Base64.getEncoder().encodeToString(pollJson.toByteArray(StandardCharsets.UTF_8))
        val payload = InteractivePayloadParser.parse(
            bundleId = "com.apple.messages.MSMessageExtensionBalloonPlugin:0000000000:com.apple.messages.Polls",
            payloadJson = """{"appName":"Polls","url":"data:,$encoded?src=p&c=2"}""",
        )

        val poll = assertIs<InteractivePayload.Poll>(payload)
        assertEquals("Lunch?", poll.question)
        assertEquals(listOf("Tacos", "Pizza"), poll.options.map { it.text })
        assertEquals(listOf(2, 0), poll.options.map { it.voteCount })
    }

    @Test
    fun `find my payload maps coordinates`() {
        val locationJson = """{"initialLocation":{"latitude":37.3349,"longitude":-122.0090},"longAddress":"Apple Park"}"""
        val encoded = Base64.getEncoder().encodeToString(locationJson.toByteArray(StandardCharsets.UTF_8))
        val payload = InteractivePayloadParser.parse(
            bundleId = "com.apple.messages.MSMessageExtensionBalloonPlugin:1:com.apple.findmy.FindMyMessagesApp",
            payloadJson = """{"appName":"Find My","ldText":"Live Location","url":"data:,$encoded"}""",
        )

        val location = assertIs<InteractivePayload.LiveLocation>(payload)
        assertEquals(37.3349, location.latitude)
        assertEquals(-122.0090, location.longitude)
        assertEquals("Apple Park", location.label)
    }

    @Test
    fun `known bundle without payload becomes calm unsupported fallback`() {
        val payload = InteractivePayloadParser.parse(
            bundleId = "com.example.messages.SomeExtension",
            payloadJson = null,
        )
        val unsupported = assertIs<InteractivePayload.Unsupported>(payload)
        assertEquals("SomeExtension", unsupported.appName)
        assertNull(unsupported.url)
    }

    @Test
    fun `mapper persists enriched app json and pairs iris companion`() {
        val appJson = """{"appName":"Polls","bundleId":"com.apple.messages.Polls","url":"data:,e30="}"""
        val mapped = MessageMapper.mapNormal(
            inst = UMessageInst(
                id = "live-photo-message",
                sender = "mailto:friend@example.com",
                conversation = UConversation(
                    participants = listOf("mailto:me@example.com", "mailto:friend@example.com"),
                    cvName = null,
                    senderGuid = null,
                    afterGuid = null,
                ),
                message = UMessage.Normal(
                    parts = listOf(
                        UIndexedPart(
                            UPart.Attachment(0uL, "public.heic", "image/heic", "IMG_0001.HEIC", false, "still"),
                            0uL,
                            null,
                        ),
                        UIndexedPart(
                            UPart.Attachment(0uL, "com.apple.quicktime-movie", "video/quicktime", "IMG_0001.MOV", true, "motion"),
                            0uL,
                            null,
                        ),
                    ),
                    effect = null,
                    replyGuid = null,
                    replyPart = null,
                    subject = null,
                    voice = false,
                    isSms = false,
                    appJson = appJson,
                    linkJson = null,
                ),
                sentTimestamp = 1uL,
                sendDelivered = false,
                verificationFailed = false,
            ),
            normal = UMessage.Normal(
                parts = listOf(
                    UIndexedPart(UPart.Attachment(0uL, "public.heic", "image/heic", "IMG_0001.HEIC", false, "still"), 0uL, null),
                    UIndexedPart(UPart.Attachment(0uL, "com.apple.quicktime-movie", "video/quicktime", "IMG_0001.MOV", true, "motion"), 0uL, null),
                ),
                effect = null,
                replyGuid = null,
                replyPart = null,
                subject = null,
                voice = false,
                isSms = false,
                appJson = appJson,
                linkJson = null,
            ),
            myHandles = setOf("mailto:me@example.com"),
        )

        assertEquals("com.apple.messages.Polls", mapped.message.balloonBundleId)
        assertEquals(appJson, mapped.message.dbPayloadData)
        assertEquals(2, mapped.attachments.size)
        val still = mapped.attachments.first()
        val motion = mapped.attachments.last()
        assertTrue(still.hasLivePhoto)
        assertEquals(motion.guid, still.metadata["livePhotoMotionGuid"])
        assertEquals(true, motion.metadata["livePhotoMotion"])
    }
}
