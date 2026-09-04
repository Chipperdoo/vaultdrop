package dev.vaultdrop.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * One result row. [snippet] is null for a name match, which has no note text
 * behind it to quote.
 */
data class SearchHit(val entry: IndexEntry, val snippet: String?)

/**
 * Full text, read on demand.
 *
 * The index holds no note content, so this is what makes search cover more than
 * filenames. It costs a read of the vault per search rather than a read of the
 * vault per launch, which is the right way round for a feature you use
 * occasionally in an app whose job is opening today's note fast. It also can't go
 * stale: it searches what is on disk at the moment you ask.
 */
class VaultSearch(private val files: VaultFiles) {

    /** [capped] means the scan stopped at the limit rather than reaching the end. */
    data class Result(val hits: List<SearchHit>, val scanned: Int, val capped: Boolean)

    /**
     * Reads every note in [index] and keeps the ones holding [query].
     *
     * Concurrent, for the reason the preview backfill was: the cost is round-trip
     * latency out to the storage provider and back, ~16 ms apiece, not throughput.
     * [CONCURRENCY] permits overlap those hops without drowning the binder pool
     * every other app on the phone is also queueing against.
     *
     * Hits are stored **by position**, so the name-sorted order [index] arrived in
     * survives reads finishing out of order. Cancellation is checked once per
     * chunk and again on every permit, so a keystroke stops the scan promptly.
     */
    suspend fun run(
        index: WikiIndex,
        query: String,
        limit: Int = LIMIT,
        onProgress: suspend (scanned: Int, hits: List<SearchHit>) -> Unit = { _, _ -> },
    ): Result {
        val q = query.trim().foldCase()
        val entries = index.all
        if (q.isEmpty() || entries.isEmpty()) return Result(emptyList(), 0, capped = false)

        val found = arrayOfNulls<SearchHit>(entries.size)
        val gate = Semaphore(CONCURRENCY)
        var scanned = 0
        var hits = 0

        for (chunk in entries.indices.chunked(PROGRESS_EVERY)) {
            currentCoroutineContext().ensureActive()
            coroutineScope {
                chunk.map { at ->
                    async(Dispatchers.IO) {
                        gate.withPermit {
                            val entry = entries[at]
                            val text = files.readHead(files.uriFor(entry.path), MAX_NOTE_BYTES)
                                ?: return@withPermit
                            val cut = text.foldCase().indexOf(q)
                            if (cut != -1) {
                                found[at] = SearchHit(entry, Snippet.around(text, cut, q.length))
                            }
                        }
                    }
                }.awaitAll()
            }
            scanned += chunk.size
            for (at in chunk) if (found[at] != null) hits++
            val collected = collect(found, limit)
            // Stopping at the limit is what keeps a common word from costing the
            // whole vault; the caller says "40+" rather than pretending it counted.
            if (hits >= limit) return Result(collected, scanned, capped = true)
            onProgress(scanned, collected)
        }
        return Result(collect(found, limit), scanned, capped = false)
    }

    private fun collect(found: Array<SearchHit?>, limit: Int): List<SearchHit> {
        val out = ArrayList<SearchHit>(minOf(limit, 16))
        for (hit in found) {
            if (hit == null) continue
            out.add(hit)
            if (out.size == limit) break
        }
        return out
    }

    private companion object {
        /**
         * Enough permits to keep the provider busy without drowning its binder
         * pool. Inherited from the preview backfill this replaces, along with the
         * measurement behind it.
         */
        const val CONCURRENCY = 12

        /** Deep enough for any diary note; bounded so one huge file cannot stall the scan. */
        const val MAX_NOTE_BYTES = 64 * 1024

        /** ~4 rounds of reads between progress ticks, so rows land as they are found. */
        const val PROGRESS_EVERY = 48

        const val LIMIT = 40
    }
}

/** The window of note text a result row quotes. Pure, so it is testable without SAF. */
object Snippet {

    private const val BEFORE = 34
    private const val AFTER = 86

    /**
     * A one-line window of [text] around the hit at [at], with runs of whitespace
     * collapsed and an ellipsis on either side that was cut.
     *
     * Flattening shifts the offset, so the window carries no index — the row finds
     * the query again inside these a hundred-odd characters, which is free.
     */
    fun around(text: String, at: Int, length: Int): String {
        val start = (at - BEFORE).coerceAtLeast(0)
        val end = (at + length + AFTER).coerceAtMost(text.length)
        return buildString {
            if (start > 0) append('…')
            appendFlattened(text, start, end)
            if (end < text.length) append('…')
        }
    }

    /** Collapses whitespace as it goes, dropping it at both ends of the window. */
    private fun StringBuilder.appendFlattened(text: String, from: Int, to: Int) {
        val mark = length
        var pending = false
        for (i in from until to) {
            val c = text[i]
            if (c.isWhitespace()) {
                pending = length > mark
            } else {
                if (pending) {
                    append(' ')
                    pending = false
                }
                append(c)
            }
        }
    }
}
