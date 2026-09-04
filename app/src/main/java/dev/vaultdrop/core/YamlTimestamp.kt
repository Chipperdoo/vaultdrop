package dev.vaultdrop.core

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The Linter's `yaml-timestamp` rule, applied to a note VaultDrop is writing.
 *
 * Obsidian only runs the plugin when Obsidian is open, so a note captured here
 * would otherwise carry no timestamps until the next time the vault is opened on
 * the desktop. This does what the rule would have done, from the same settings.
 */
object YamlTimestamp {

    /**
     * @return the frontmatter to write, stamped per [config].
     *
     * [isNew] is the difference between the two halves of the rule: `date
     * modified` moves on every save, `date created` is written once, as the file
     * is created. VaultDrop never backfills a creation date onto a note it did
     * not create — SAF exposes no creation time to recover one from, and the
     * plugin's own "source of truth" setting says a value already in the
     * frontmatter wins anyway.
     *
     * @param body the note's body, when the caller has it. Read only to decide
     *   whether minting a fence is safe, and which line ending to mint it with.
     */
    fun stamp(
        frontmatter: String,
        config: LinterConfig,
        relativePath: String,
        now: LocalDateTime,
        isNew: Boolean,
        body: String = "",
    ): String {
        if (!config.appliesTo(relativePath)) return frontmatter

        // Belt and braces against a fence shape [Frontmatter.split] did not
        // recognise. It should have handed any fence over as frontmatter, so an
        // empty one against a body that still opens with `---` means the note
        // would get a second fence bolted above its first — which demotes
        // everything below it to body text. Leaving the note unstamped is the
        // recoverable failure of the two; the next save tries again.
        if (frontmatter.isEmpty() && Frontmatter.opensWithFence(body)) return frontmatter

        // "Convert local time to UTC" shifts the same instant, rather than
        // reading a different clock, so a stamped time still matches the save.
        val at = if (config.utc) {
            now.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()
        } else {
            now
        }
        val value = config.stamp(at)
        // A fence minted from nothing takes the body's ending, so a CRLF note
        // does not end up with an LF header bolted to the top of it.
        val newline = Frontmatter.newlineOf(body)
        var out = frontmatter

        if (isNew && config.stampCreated) {
            val keep = config.createdFromFrontmatter && Frontmatter.has(out, config.createdKey)
            if (!keep) out = Frontmatter.upsert(out, config.createdKey, value, newline)
        }
        if (config.stampModified) {
            out = Frontmatter.upsert(out, config.modifiedKey, value, newline)
        }
        return out
    }
}
