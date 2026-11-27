package software.larsartmann.webphone.call

import android.app.Application
import android.telecom.CallAttributes
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The outbound Telecom fact sheet must say OUTGOING. The Builder's
 * second argument is the call direction and the call-type constants
 * share its integers, so a wrong constant compiles cleanly and tells
 * Telecom that the call the user just dialed is an incoming one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 34])
class TelecomOutgoingAttributesTest {
    @Test
    fun `an outbound placement is declared outgoing, never incoming`() {
        val app = ApplicationProvider.getApplicationContext<Application>()

        val attributes = TelecomOutgoing.outgoingCallAttributes(app, "100")

        assertEquals(CallAttributes.DIRECTION_OUTGOING, attributes.direction)
    }
}
