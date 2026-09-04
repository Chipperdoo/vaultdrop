package dev.vaultdrop.core

import org.json.JSONObject

/**
 * The Linter plugin's `yaml-timestamp` rule, read live out of the vault.
 *
 * The plugin keeps its settings where every Obsidian community plugin does —
 * `.obsidian/plugins/obsidian-linter/data.json` — so this is the same kind of
 * file as `daily-notes.json` and is read the same way: through [VaultFiles],
 * with the mtime and hash kept so Phase 2 can notice it moved, and cached in
 * `SharedPreferences` so the critical path never has to open it.
 *
 * Nothing here is a VaultDrop setting. Every value is the user's, edited in
 * Obsidian; when the rule is off in the plugin it is off here.
 */
data class LinterConfig(
    /** The `yaml-timestamp` rule's own toggle. */
    val enabled: Boolean = false,
    val stampCreated: Boolean = false,
    val createdKey: String = DEFAULT_CREATED_KEY,
    /**
     * True when "Date created source of truth" is `frontmatter` — the value
     * already in the note wins, so a template that writes its own is left alone.
     */
    val createdFromFrontmatter: Boolean = true,
    val stampModified: Boolean = false,
    val modifiedKey: String = DEFAULT_MODIFIED_KEY,
    /** Moment format, translated by [MomentFormat] like every other one. */
    val format: String = DEFAULT_FORMAT,
    val utc: Boolean = false,
    /** `foldersToIgnore`, as vault-relative folder paths. */
    val ignoredFolders: List<String> = emptyList(),
    /** `filesToIgnore`, as the plugin's regexes against the vault-relative path. */
    val ignoredFiles: List<Regex> = emptyList(),
    /** False when the plugin is not installed and these are pure defaults. */
    val present: Boolean = false,
    val sha256: String = "",
    val lastModified: Long = 0L,
    val lastReadAt: Long = 0L,
) {

    /** True when the rule would stamp a note at [relativePath]. */
    fun appliesTo(relativePath: String): Boolean =
        enabled && (stampCreated || stampModified) && !isIgnored(relativePath)

    private fun isIgnored(relativePath: String): Boolean {
        if (ignoredFolders.any { relativePath.startsWith("$it/") }) return true
        return ignoredFiles.any { it.containsMatchIn(relativePath) }
    }

    /** What the plugin would write into the note right now. */
    fun stamp(now: java.time.LocalDateTime): String =
        MomentFormat.formatter(format).format(now)

    fun toJson(): String = JSONObject().apply {
        put(KEY_ENABLED, enabled)
        put(KEY_CREATED, stampCreated)
        put(KEY_CREATED_KEY, createdKey)
        put(KEY_CREATED_TRUTH, createdFromFrontmatter)
        put(KEY_MODIFIED, stampModified)
        put(KEY_MODIFIED_KEY, modifiedKey)
        put(KEY_FORMAT, format)
        put(KEY_UTC, utc)
        put(KEY_FOLDERS, ignoredFolders.joinToString("\n"))
        put(KEY_FILES, ignoredFiles.joinToString("\n") { it.pattern })
        put(KEY_PRESENT, present)
        put(KEY_SHA, sha256)
        put(KEY_MTIME, lastModified)
        put(KEY_READ_AT, lastReadAt)
    }.toString()

    companion object {
        /** Where every Obsidian community plugin keeps its settings. */
        const val PATH = ".obsidian/plugins/obsidian-linter/data.json"

        const val DEFAULT_CREATED_KEY = "date created"
        const val DEFAULT_MODIFIED_KEY = "date modified"
        const val DEFAULT_FORMAT = "YYYY-MM-DD HH:mm"

        /** The plugin's own rule id, and the keys it stores under it. */
        private const val RULES = "ruleConfigs"
        private const val RULE = "yaml-timestamp"
        private const val R_ENABLED = "enabled"
        private const val R_CREATED = "date-created"
        private const val R_CREATED_KEY = "date-created-key"
        private const val R_CREATED_TRUTH = "date-created-source-of-truth"
        private const val R_MODIFIED = "date-modified"
        private const val R_MODIFIED_KEY = "date-modified-key"
        private const val R_FORMAT = "format"
        private const val R_UTC = "convert-to-utc"
        private const val TRUTH_FRONTMATTER = "frontmatter"
        private const val IGNORE_FOLDERS = "foldersToIgnore"
        private const val IGNORE_FILES = "filesToIgnore"

        private const val KEY_ENABLED = "enabled"
        private const val KEY_CREATED = "created"
        private const val KEY_CREATED_KEY = "createdKey"
        private const val KEY_CREATED_TRUTH = "createdFromFrontmatter"
        private const val KEY_MODIFIED = "modified"
        private const val KEY_MODIFIED_KEY = "modifiedKey"
        private const val KEY_FORMAT = "format"
        private const val KEY_UTC = "utc"
        private const val KEY_FOLDERS = "ignoredFolders"
        private const val KEY_FILES = "ignoredFiles"
        private const val KEY_PRESENT = "present"
        private const val KEY_SHA = "sha256"
        private const val KEY_MTIME = "lastModified"
        private const val KEY_READ_AT = "lastReadAt"

        /** Parses the plugin's own file. A missing rule block means the rule is off. */
        fun parse(raw: String, sha256: String, lastModified: Long, now: Long): LinterConfig {
            val json = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
            val rule = json.optJSONObject(RULES)?.optJSONObject(RULE)
            return LinterConfig(
                enabled = rule?.optBoolean(R_ENABLED, false) ?: false,
                stampCreated = rule?.optBoolean(R_CREATED, false) ?: false,
                createdKey = rule?.optString(R_CREATED_KEY, "").orEmpty()
                    .ifBlank { DEFAULT_CREATED_KEY },
                createdFromFrontmatter =
                    rule?.optString(R_CREATED_TRUTH, TRUTH_FRONTMATTER) == TRUTH_FRONTMATTER,
                stampModified = rule?.optBoolean(R_MODIFIED, false) ?: false,
                modifiedKey = rule?.optString(R_MODIFIED_KEY, "").orEmpty()
                    .ifBlank { DEFAULT_MODIFIED_KEY },
                format = rule?.optString(R_FORMAT, "").orEmpty().ifBlank { DEFAULT_FORMAT },
                utc = rule?.optBoolean(R_UTC, false) ?: false,
                ignoredFolders = stringList(json, IGNORE_FOLDERS),
                ignoredFiles = regexList(json, IGNORE_FILES),
                present = true,
                sha256 = sha256,
                lastModified = lastModified,
                lastReadAt = now,
            )
        }

        private fun stringList(json: JSONObject, key: String): List<String> {
            val array = json.optJSONArray(key) ?: return emptyList()
            return (0 until array.length())
                .mapNotNull { array.optString(it, "").trim('/').ifBlank { null } }
        }

        /**
         * `filesToIgnore` entries are `{ match, flags }` — JavaScript regexes.
         * One that `java.util.regex` will not take is dropped rather than being
         * treated as a match, so a pattern we cannot read never silently turns
         * the whole feature off.
         */
        private fun regexList(json: JSONObject, key: String): List<Regex> {
            val array = json.optJSONArray(key) ?: return emptyList()
            return (0 until array.length()).mapNotNull { i ->
                val entry = array.optJSONObject(i) ?: return@mapNotNull null
                val pattern = entry.optString("match", "").ifBlank { return@mapNotNull null }
                val options = buildSet {
                    val flags = entry.optString("flags", "")
                    if (flags.contains('i')) add(RegexOption.IGNORE_CASE)
                    if (flags.contains('m')) add(RegexOption.MULTILINE)
                    if (flags.contains('s')) add(RegexOption.DOT_MATCHES_ALL)
                }
                runCatching { Regex(pattern, options) }.getOrNull()
            }
        }

        /** Rehydrates the SharedPreferences blob written on the previous run. */
        fun fromCache(blob: String): LinterConfig? = runCatching {
            val json = JSONObject(blob)
            LinterConfig(
                enabled = json.optBoolean(KEY_ENABLED, false),
                stampCreated = json.optBoolean(KEY_CREATED, false),
                createdKey = json.optString(KEY_CREATED_KEY, DEFAULT_CREATED_KEY),
                createdFromFrontmatter = json.optBoolean(KEY_CREATED_TRUTH, true),
                stampModified = json.optBoolean(KEY_MODIFIED, false),
                modifiedKey = json.optString(KEY_MODIFIED_KEY, DEFAULT_MODIFIED_KEY),
                format = json.optString(KEY_FORMAT, DEFAULT_FORMAT),
                utc = json.optBoolean(KEY_UTC, false),
                ignoredFolders = json.optString(KEY_FOLDERS, "").lines().filter { it.isNotBlank() },
                ignoredFiles = json.optString(KEY_FILES, "").lines()
                    .filter { it.isNotBlank() }
                    .mapNotNull { runCatching { Regex(it) }.getOrNull() },
                present = json.optBoolean(KEY_PRESENT, false),
                sha256 = json.optString(KEY_SHA, ""),
                lastModified = json.optLong(KEY_MTIME, 0L),
                lastReadAt = json.optLong(KEY_READ_AT, 0L),
            )
        }.getOrNull()
    }
}
