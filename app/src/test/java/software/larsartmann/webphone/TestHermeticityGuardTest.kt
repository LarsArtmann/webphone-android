package software.larsartmann.webphone

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The suite never touches the live stack. The wired paths that start
 * real network work from a Robolectric launch (the shell's
 * `CallLayer.ensureWired`, the ring router, the
 * session-dead teardown's server-row DELETE) each carry a test seam
 * that turns the wire off, and three test classes shipped without it
 * until 2026-11-25: every run then sent a real `GET /api/session` to
 * production. This guard reads the test SOURCES: any test file that
 * reaches a wired entry point must flip its seam. A new test that
 * forgets the seam reds here, naming the file, before it ever runs.
 *
 * Deliberately NOT in the rule: `NtfySubscriptionService` tests (they
 * hand the service an explicit URL, a MockWebServer or none) and the
 * call screen/service/dialer tests (they render and start no wire).
 */
class TestHermeticityGuardTest {
    private val testRoot: File = File("src/test/java")

    private fun kotlinSources(): Sequence<File> =
        testRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }

    private fun requiresWiringOff(source: String): Boolean =
        listOf(
            "MainActivity::class.java",
            "RingEvent.",
            "ensureWired",
        ).any { source.contains(it) }

    private fun requiresDeregistrationSeam(source: String): Boolean = source.contains("onSessionDead")

    @Test
    fun `every test that reaches a wired entry point turns the wire off`() {
        val missingWiringSeam =
            kotlinSources()
                .filter { file ->
                    val source = file.readText()
                    requiresWiringOff(source) && !source.contains("wiringEnabled = false")
                }.map { it.path }
                .toList()
        val missingDeregisterSeam =
            kotlinSources()
                .filter { file ->
                    val source = file.readText()
                    requiresDeregistrationSeam(source) && !source.contains("deregisterPushDevice = ")
                }.map { it.path }
                .toList()

        assertTrue(
            missingWiringSeam.isEmpty(),
            "these tests reach a wired path without `CallLayer.wiringEnabled = false` " +
                "(they would phone the live stack): $missingWiringSeam",
        )
        assertTrue(
            missingDeregisterSeam.isEmpty(),
            "these tests run the session-dead teardown without the `deregisterPushDevice` seam " +
                "(the server-row DELETE would go to the live stack): $missingDeregisterSeam",
        )
    }
}
