package dev.vaultdrop.core

import org.json.JSONObject

/**
 * The parsed contents of `.obsidian/daily-notes.json`, plus the provenance we
 * need in Phase 2 to notice the file changed underneath us.
 */
data class VaultConfig(
    val format: String = DEFAULT_FORMAT,
    val folder: String = DEFAULT_FOLDER,
    val template: String = DEFAULT_TEMPLATE,
    /** False when the config file was absent and these are pure defaults. */
    val present: Boolean = true,
    val sha256: String = "",
    val lastModified: Long = 0L,
    val lastReadAt: Long = 0L,
) {
    fun toJson(): String = JSONObject().apply {
        put(KEY_FORMAT, format)
        put(KEY_FOLDER, folder)
        put(KEY_TEMPLATE, template)
        put(KEY_PRESENT, present)
        put(KEY_SHA, sha256)
        put(KEY_MTIME, lastModified)
        put(KEY_READ_AT, lastReadAt)
    }.toString()

    /** True when the daily-note *location* changed; a template-only edit is not a rename. */
    fun locationDiffers(other: VaultConfig): Boolean =
        format != other.format || folder != other.folder

    companion object {
        const val DEFAULT_FORMAT = "YYYY-MM-DD"
        const val DEFAULT_FOLDER = ""
        const val DEFAULT_TEMPLATE = ""

        private const val KEY_FORMAT = "format"
        private const val KEY_FOLDER = "folder"
        private const val KEY_TEMPLATE = "template"
        private const val KEY_PRESENT = "present"
        private const val KEY_SHA = "sha256"
        private const val KEY_MTIME = "lastModified"
        private const val KEY_READ_AT = "lastReadAt"

        /** Parses the vault's own file. Missing or malformed keys fall back per the brief. */
        fun parse(raw: String, sha256: String, lastModified: Long, now: Long): VaultConfig {
            val json = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
            return VaultConfig(
                format = json.optString(KEY_FORMAT, "").ifBlank { DEFAULT_FORMAT },
                folder = json.optString(KEY_FOLDER, DEFAULT_FOLDER).trim('/'),
                template = json.optString(KEY_TEMPLATE, DEFAULT_TEMPLATE),
                present = true,
                sha256 = sha256,
                lastModified = lastModified,
                lastReadAt = now,
            )
        }

        /** Rehydrates the SharedPreferences blob written on the previous run. */
        fun fromCache(blob: String): VaultConfig? = runCatching {
            val json = JSONObject(blob)
            VaultConfig(
                format = json.optString(KEY_FORMAT, DEFAULT_FORMAT),
                folder = json.optString(KEY_FOLDER, DEFAULT_FOLDER),
                template = json.optString(KEY_TEMPLATE, DEFAULT_TEMPLATE),
                present = json.optBoolean(KEY_PRESENT, true),
                sha256 = json.optString(KEY_SHA, ""),
                lastModified = json.optLong(KEY_MTIME, 0L),
                lastReadAt = json.optLong(KEY_READ_AT, 0L),
            )
        }.getOrNull()
    }
}
