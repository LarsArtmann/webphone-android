package software.larsartmann.webphone.shell

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceIdentityTest {

    @Test
    fun `generated topic is 32 lowercase hex characters`() {
        val topic = DeviceIdentity.generateTopic(Random(42))
        assertEquals(32, topic.length)
        assertTrue(topic.matches(Regex("^[0-9a-f]{32}$")), "topic was: $topic")
    }

    @Test
    fun `topic generation is deterministic for a given random source`() {
        assertEquals(
            DeviceIdentity.generateTopic(Random(7)),
            DeviceIdentity.generateTopic(Random(7)),
        )
    }

    @Test
    fun `distinct seeds produce distinct topics`() {
        val topics = (0 until 16).map { DeviceIdentity.generateTopic(Random(it.toLong())) }
        assertEquals(16, topics.toSet().size)
    }
}
