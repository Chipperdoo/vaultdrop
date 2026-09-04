package dev.vaultdrop.core

import java.time.LocalDate

/**
 * The days that already have a note, ascending.
 *
 * Day navigation used to be `date ± 1` arithmetic, which meant the vault only
 * found out you had stepped onto an empty day once [VaultRepository.openNote]
 * created the file. This is what lets the chevrons and the swipe skip a gap
 * instead of filling it: they ask for the nearest note either side of the day on
 * screen, and creating one stays the day sheet's job alone.
 */
class DayIndex(val days: List<LocalDate>) {

    val size: Int get() = days.size

    fun contains(date: LocalDate): Boolean = indexOf(date) >= 0

    /** The nearest day with a note strictly before [date], or null at the edge. */
    fun before(date: LocalDate): LocalDate? = days.getOrNull(insertionPoint(date) - 1)

    /** The nearest day with a note strictly after [date], or null at the edge. */
    fun after(date: LocalDate): LocalDate? {
        val at = insertionPoint(date)
        // insertionPoint lands *on* an exact hit, so step over it.
        return days.getOrNull(if (at < days.size && days[at] == date) at + 1 else at)
    }

    /**
     * A copy with [changes] applied — true adds the day, false drops it.
     *
     * Both directions are needed. The index is derived from a walk that is always
     * a little behind: a note created through the day sheet is not in it yet, and
     * one deleted in Obsidian since the walk still is.
     */
    fun with(changes: Map<LocalDate, Boolean>): DayIndex {
        if (changes.isEmpty()) return this
        val out = ArrayList<LocalDate>(days.size + changes.size)
        for (day in days) if (changes[day] != false) out.add(day)
        for ((day, present) in changes) if (present && indexOf(day) < 0) out.add(day)
        out.sort()
        return DayIndex(out)
    }

    fun plus(date: LocalDate): DayIndex = with(mapOf(date to true))

    fun minus(date: LocalDate): DayIndex = with(mapOf(date to false))

    private fun indexOf(date: LocalDate): Int {
        val at = insertionPoint(date)
        return if (at < days.size && days[at] == date) at else -1
    }

    /** The first position holding a day >= [date]; `days.size` when there is none. */
    private fun insertionPoint(date: LocalDate): Int {
        var low = 0
        var high = days.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (days[mid] < date) low = mid + 1 else high = mid
        }
        return low
    }

    companion object {
        val EMPTY = DayIndex(emptyList())

        /**
         * Which of the [span] days ending at [today] have a note under [config].
         *
         * Derived *forwards* through [DailyNote.resolve] — the same call
         * [VaultRepository.openNote] writes through — so the answer cannot
         * disagree with where a note actually lives. Parsing the filenames in
         * [paths] back into dates would be the obvious inverse and is not
         * available: [MomentFormat] emits Moment's ordinals (`Do`, `Mo`) as
         * literals, and a format carrying no year has no inverse at all.
         *
         * The cost is one cached-formatter call and one hash lookup per candidate
         * day, so a ten-year span is a few milliseconds — cheap enough to run on
         * the same background pass that builds the index it reads.
         */
        fun build(
            config: VaultConfig,
            paths: Set<String>,
            today: LocalDate,
            span: Int,
        ): DayIndex {
            if (paths.isEmpty()) return EMPTY
            val out = ArrayList<LocalDate>()
            for (back in span - 1 downTo 0) {
                val date = today.minusDays(back.toLong())
                if (DailyNote.resolve(config, date).relativePath in paths) out.add(date)
            }
            return DayIndex(out)
        }
    }
}
