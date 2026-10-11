package dev.vaultdrop.core

import org.json.JSONObject

/**
 * Obsidian's "Deleted files" setting, in Obsidian's own words.
 *
 * The keys are the strings the vault stores, so a value we do not recognise
 * falls back to [DEFAULT] rather than inventing behaviour.
 */
enum class TrashOption(val key: String) {
    /** Move to the OS trash. Android gives a folder-grant app no way to do this. */
    System("system"),

    /** Move to the vault's own `.trash/`. */
    Local("local"),

    /** Delete outright, with no copy kept. */
    None("none");

    companion object {
        /** What Obsidian does when the key is absent: move to the system trash. */
        val DEFAULT = System

        fun from(raw: String): TrashOption = entries.firstOrNull { it.key == raw } ?: DEFAULT
    }
}

/**
 * Obsidian's "New link format" setting, in Obsidian's own words.
 *
 * Keyed by the strings the vault stores, like [TrashOption], so a value we do
 * not recognise falls back to [DEFAULT].
 */
enum class LinkFormat(val key: String) {
    /** The bare file name, unless another file in the vault shares it. */
    Shortest("shortest"),

    /** The path from the note's own folder. */
    Relative("relative"),

    /** The path from the vault root. */
    Absolute("absolute");

    companion object {
        /** What Obsidian does when the key is absent. */
        val DEFAULT = Shortest

        fun from(raw: String): LinkFormat = entries.firstOrNull { it.key == raw } ?: DEFAULT
    }
}

/**
 * `.obsidian/app.json` — the vault's own application settings.
 *
 * Read exactly the way [LinterConfig] and [VaultConfig] are: through
 * [VaultFiles], with the mtime and hash kept so Phase 2 can notice the file
 * moved, and cached in `SharedPreferences` so the critical path never opens it.
 *
 * Nothing here is a VaultDrop setting. Deleting a note is the user's own
 * `trashOption`, edited in Obsidian, so a vault carried to another device
 * behaves the same way there. Where an attached file goes and how the note
 * links to it are the user's own settings too.
 */
data class AppConfig(
    val trash: TrashOption = TrashOption.DEFAULT,
    /** `attachmentFolderPath` as stored. [Attachments.folderFor] reads it. */
    val attachmentFolder: String = DEFAULT_ATTACHMENT_FOLDER,
    /** `useMarkdownLinks`: `![](…)` rather than `![[…]]`. */
    val markdownLinks: Boolean = false,
    val linkFormat: LinkFormat = LinkFormat.DEFAULT,
    override val present: Boolean = false,
    override val sha256: String = "",
    override val lastModified: Long = 0L,
    val lastReadAt: Long = 0L,
) : Probed {
    fun toJson(): String = JSONObject().apply {
        put(KEY_TRASH, trash.key)
        put(KEY_ATTACHMENTS, attachmentFolder)
        put(KEY_MARKDOWN_LINKS, markdownLinks)
        put(KEY_LINK_FORMAT, linkFormat.key)
        put(KEY_PRESENT, present)
        put(KEY_SHA, sha256)
        put(KEY_MTIME, lastModified)
        put(KEY_READ_AT, lastReadAt)
    }.toString()

    companion object {
        const val PATH = ".obsidian/app.json"

        /** Obsidian's default when the key is absent: the vault root. */
        const val DEFAULT_ATTACHMENT_FOLDER = "/"

        /** Obsidian's keys. */
        private const val TRASH_OPTION = "trashOption"
        private const val ATTACHMENT_FOLDER_PATH = "attachmentFolderPath"
        private const val USE_MARKDOWN_LINKS = "useMarkdownLinks"
        private const val NEW_LINK_FORMAT = "newLinkFormat"

        private const val KEY_TRASH = "trash"
        private const val KEY_ATTACHMENTS = "attachmentFolder"
        private const val KEY_MARKDOWN_LINKS = "markdownLinks"
        private const val KEY_LINK_FORMAT = "linkFormat"
        private const val KEY_PRESENT = "present"
        private const val KEY_SHA = "sha256"
        private const val KEY_MTIME = "lastModified"
        private const val KEY_READ_AT = "lastReadAt"

        /** Parses the vault's own file. A missing key is Obsidian's default, not an error. */
        fun parse(raw: String, sha256: String, lastModified: Long, now: Long): AppConfig {
            val json = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
            return AppConfig(
                trash = TrashOption.from(json.optString(TRASH_OPTION, "")),
                attachmentFolder = json.optString(ATTACHMENT_FOLDER_PATH, DEFAULT_ATTACHMENT_FOLDER),
                markdownLinks = json.optBoolean(USE_MARKDOWN_LINKS, false),
                linkFormat = LinkFormat.from(json.optString(NEW_LINK_FORMAT, "")),
                present = true,
                sha256 = sha256,
                lastModified = lastModified,
                lastReadAt = now,
            )
        }

        /**
         * Rehydrates the SharedPreferences blob written on the previous run.
         *
         * A blob written before the attachment settings were cached has none of
         * them, and its stamp still matches the file, so the probe would keep the
         * defaults until `app.json` next changed. It comes back as not present
         * instead, which makes the next probe read the file.
         */
        fun fromCache(raw: String): AppConfig {
            val json = runCatching { JSONObject(raw) }.getOrElse { return AppConfig() }
            val current = json.has(KEY_ATTACHMENTS)
            return AppConfig(
                trash = TrashOption.from(json.optString(KEY_TRASH, "")),
                attachmentFolder = json.optString(KEY_ATTACHMENTS, DEFAULT_ATTACHMENT_FOLDER),
                markdownLinks = json.optBoolean(KEY_MARKDOWN_LINKS, false),
                linkFormat = LinkFormat.from(json.optString(KEY_LINK_FORMAT, "")),
                present = current && json.optBoolean(KEY_PRESENT, false),
                sha256 = json.optString(KEY_SHA, ""),
                lastModified = json.optLong(KEY_MTIME, 0L),
                lastReadAt = json.optLong(KEY_READ_AT, 0L),
            )
        }
    }
}
