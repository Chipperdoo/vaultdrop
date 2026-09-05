package dev.vaultdrop.core

import android.content.Context
import java.io.File

/**
 * One vault note as the index knows it: its name and where it lives, and nothing
 * else.
 *
 * The index deliberately carries no note content. The `[[` picker matches on
 * [name], [DayIndex] is built from [path], and opening a search result reads the
 * file anyway — so a cached preview only ever served full-text search, and paid
 * for it with a file open per note on every walk. Full text is [VaultSearch]'s
 * job now, on demand, against what the vault says at the time you ask.
 */
data class IndexEntry(
    val name: String,
    val path: String,
) {
    /**
     * [name] folded once, at build time, so matching is an intrinsic `indexOf`
     * rather than Kotlin's `ignoreCase` overload — which is a per-position loop
     * through `Character.toUpperCase`, and used to run on the main thread for
     * every note on every keystroke.
     */
    val nameLower: String = name.foldCase()
}

class WikiIndex(val all: List<IndexEntry>) {

    val size: Int get() = all.size

    /**
     * Every note's path, so [DayIndex] can test a candidate day in a hash lookup.
     *
     * A set rather than the `path -> entry` map this used to be: nothing ever
     * wanted the entry, so half of every bucket was an address no caller read.
     */
    val paths: Set<String> by lazy(LazyThreadSafetyMode.NONE) {
        all.mapTo(HashSet(all.size), IndexEntry::path)
    }

    /** What [nameHits] found: the rows to show, and how many there were in all. */
    data class NameHits(val matches: List<IndexEntry>, val total: Int)

    /**
     * Name matching for both the `[[` strip and the search screen's live filter:
     * prefix matches first, then contains.
     *
     * One pass. The count used to be a second full scan of its own with no early
     * exit, so every keystroke inside a `[[` walked the whole vault twice.
     */
    fun nameHits(query: String, limit: Int): NameHits {
        val q = query.trim().foldCase()
        if (q.isEmpty()) return NameHits(all.take(limit), all.size)
        val prefix = ArrayList<IndexEntry>(limit)
        val contains = ArrayList<IndexEntry>(limit)
        var total = 0
        for (e in all) {
            val at = e.nameLower.indexOf(q)
            if (at == -1) continue
            total++
            if (at == 0) {
                if (prefix.size < limit) prefix.add(e)
            } else if (contains.size < limit) {
                contains.add(e)
            }
        }
        return NameHits((prefix + contains).take(limit), total)
    }

    companion object {
        val EMPTY = WikiIndex(emptyList())

        private const val CACHE_FILE = "wiki-index.tsv"

        fun cacheFile(context: Context): File = File(context.filesDir, CACHE_FILE)

        /**
         * A cache written by a version that still stored previews and stamps has
         * those in columns three and up. Name and path have always been the first
         * two, so an old file still loads — and the first walk of the session
         * rewrites it without the note text.
         */
        fun load(context: Context): WikiIndex? {
            val file = cacheFile(context)
            if (!file.exists()) return null
            return runCatching {
                val entries = file.bufferedReader().useLines { lines ->
                    lines.mapNotNull { line ->
                        // Indices, not split(): a split allocated an array and a
                        // string per column for every note in the vault.
                        val tab = line.indexOf('\t')
                        if (tab <= 0) return@mapNotNull null
                        val rest = line.indexOf('\t', tab + 1)
                        val end = if (rest == -1) line.length else rest
                        if (end <= tab + 1) return@mapNotNull null
                        IndexEntry(line.substring(0, tab), line.substring(tab + 1, end))
                    }.toList()
                }
                WikiIndex(entries)
            }.getOrNull()
        }

        fun save(context: Context, index: WikiIndex) {
            runCatching {
                cacheFile(context).bufferedWriter().use { out ->
                    for (e in index.all) {
                        out.append(e.name.sanitizeField())
                        out.append('\t')
                        out.append(e.path.sanitizeField())
                        out.append('\n')
                    }
                }
            }
        }

        /**
         * When the cache was last written, or 0 when there is none.
         *
         * The launch pass uses this to decide whether the vault is worth walking
         * again at all — a walk is one cursor per directory, into the same
         * provider the editor has just finished reading from.
         */
        fun savedAt(context: Context): Long = cacheFile(context).lastModified()

        fun clear(context: Context) {
            runCatching { cacheFile(context).delete() }
        }

        private fun String.sanitizeField(): String = replace('\t', ' ').replace('\r', ' ')
    }
}

/**
 * Lowercased char for char, so an offset into the result is the same offset into
 * the original.
 *
 * `String.lowercase()` is not safe for that: a handful of codepoints change
 * length under it, which would slide every hit after them and make a snippet
 * window point at the wrong text. This is also exactly the folding Kotlin's
 * `ignoreCase` comparisons do, so nothing matches differently than before.
 */
internal fun String.foldCase(): String = String(CharArray(length) { this[it].lowercaseChar() })
