package software.larsartmann.webphone.shell

import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog
import java.io.ByteArrayOutputStream
import kotlin.test.Test

@RunWith(RobolectricTestRunner::class)
class HermeticityLeakProbeTest {
    @Test
    fun probe() {
        val out = ByteArrayOutputStream()
        ShadowLog.stream = out
        CallLayer.wiringEnabled = true
        val started = System.currentTimeMillis()
        CallLayer.ensureWired(ApplicationProvider.getApplicationContext())
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline && !out.toString().contains("/api/session")) {
            Thread.sleep(200)
        }
        println("PROBE OUTPUT after ${System.currentTimeMillis() - started} ms: ${out.toString().take(2000)}")
        ShadowLog.stream = null
    }
}
