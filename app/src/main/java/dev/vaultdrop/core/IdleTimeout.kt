package dev.vaultdrop.core

/**
 * When to give up on an Open Transcribe engine that has gone quiet.
 *
 * The contract asks for an idle timeout measured from the last callback, not a
 * deadline on the whole request. A long dictation takes a long time to
 * transcribe, and a deadline would throw away work that was going fine.
 *
 * The clock only runs once VaultDrop is waiting on the engine: after Stop, or
 * once a file has been handed over. While the mic is open a quiet engine is
 * normal. Scrib reports at pauses, so someone who talks for a minute without
 * one hears nothing back for that minute.
 *
 * [check] is meant to run every [checkEveryMs]. One that comes round much
 * later than that means nothing here was running: the app was frozen in the
 * background, or the phone was asleep, and an engine that only this app keeps
 * bound was frozen with it. That gap is nobody's silence, so the clock starts
 * again rather than counting it.
 */
class IdleTimeout(private val limitMs: Long, private val checkEveryMs: Long) {

    private var since: Long? = null
    private var lastCheck: Long = 0

    /** Stop was pressed or the file went over, so the clock starts. Only the first call counts. */
    fun waiting(now: Long) {
        if (since != null) return
        since = now
        lastCheck = now
    }

    /** A callback arrived. Restarts the clock if it is running; before that it changes nothing. */
    fun heard(now: Long) {
        if (since != null) since = now
    }

    /** Milliseconds left, 0 once it has run out, or null while nothing is being waited for. */
    fun remaining(now: Long): Long? = since?.let { (it + limitMs - now).coerceAtLeast(0) }

    /** The periodic check. True once the engine has been quiet for the whole limit. */
    fun check(now: Long): Boolean {
        if (since == null) return false
        if (now - lastCheck > checkEveryMs * STALLED_AFTER_CHECKS) heard(now)
        lastCheck = now
        return remaining(now) == 0L
    }

    private companion object {
        /** How many check periods a gap has to span to count as time nobody was running. */
        const val STALLED_AFTER_CHECKS = 3
    }
}
