package software.larsartmann.webphone.shell

/**
 * The one-wire-at-a-time guard for [CallLayer]: exactly one SipEngine
 * may attach per process lifetime-of-a-wiring, and a [invalidate]
 * (shutdown) anoints every in-flight wire attempt as an orphan — an
 * orphaned attempt must NOT attach AFTER the shutdown it raced (the
 * settings-save flow would otherwise resurrect the old origin's
 * engine right after tearing it down). The next ensureWired begins a
 * fresh generation and wins.
 *
 * Pure and JVM-testable — the invariant lives here, not in the
 * coroutine soup around it.
 */
internal class AttachGuard<T : Any> {

    @Volatile private var generation = 0L
    private val lock = Any()
    private var attached: T? = null

    /** Claim a generation for a new wiring attempt. */
    fun begin(): Long = ++generation

    /** The engine currently wired, if any. */
    fun current(): T? = attached

    /** Attach only if nothing is attached AND the attempt's generation
     *  is still the live one (shutdown invalidates older ones). */
    fun attach(candidate: T, attemptGeneration: Long): Boolean = synchronized(lock) {
        if (attached != null || attemptGeneration != generation) return false
        attached = candidate
        true
    }

    /** Shutdown: invalidate every in-flight attempt, then forget the
     *  attached engine (detach is the caller's job — it may need the
     *  engine reference to disconnect). */
    fun invalidate() {
        generation += 1
    }

    fun clear() {
        synchronized(lock) { attached = null }
    }
}
