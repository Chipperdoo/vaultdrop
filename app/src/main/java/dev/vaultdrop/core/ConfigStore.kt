package dev.vaultdrop.core

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit

/**
 * The Phase 1 source of truth. Everything the critical path needs is in memory
 * after one SharedPreferences read; the vault itself is not touched until we
 * open today's note.
 */
class ConfigStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var vaultUri: Uri?
        get() = prefs.getString(KEY_VAULT, null)?.let(Uri::parse)
        set(value) = prefs.edit { putString(KEY_VAULT, value?.toString()) }

    fun loadConfig(): VaultConfig? =
        prefs.getString(KEY_CONFIG, null)?.let(VaultConfig::fromCache)

    fun saveConfig(config: VaultConfig) {
        prefs.edit { putString(KEY_CONFIG, config.toJson()) }
    }

    /**
     * The Linter's `yaml-timestamp` settings, cached for the same reason the
     * daily-note settings are: a note created before the first frame has to be
     * stamped, and the critical path cannot afford to open the plugin's file.
     */
    fun loadLinter(): LinterConfig =
        prefs.getString(KEY_LINTER, null)?.let(LinterConfig::fromCache) ?: LinterConfig()

    fun saveLinter(config: LinterConfig) {
        prefs.edit { putString(KEY_LINTER, config.toJson()) }
    }

    /**
     * `.obsidian/app.json`, cached like the rest. Deleting a note has to know the
     * vault's `trashOption` before it can offer the choice, and the confirmation
     * appears well before Phase 2 has necessarily re-read anything.
     */
    fun loadApp(): AppConfig =
        prefs.getString(KEY_APP, null)?.let(AppConfig::fromCache) ?: AppConfig()

    fun saveApp(config: AppConfig) {
        prefs.edit { putString(KEY_APP, config.toJson()) }
    }

    /**
     * Whether the "your vault asks for the system trash" explanation has been
     * shown. Once is enough: it explains a permanent property of the platform,
     * not a condition that will clear on its own.
     */
    var trashFallbackExplained: Boolean
        get() = prefs.getBoolean(KEY_TRASH_EXPLAINED, false)
        set(value) = prefs.edit { putBoolean(KEY_TRASH_EXPLAINED, value) }

    /**
     * Whether the card saying this vault runs on Obsidian's default Daily Notes
     * settings has been put away. It describes the vault rather than a fault,
     * so once read it stays out of the way. A different vault is a different
     * story, and [clear] forgets this with the rest.
     */
    var defaultsNoted: Boolean
        get() = prefs.getBoolean(KEY_DEFAULTS_NOTED, false)
        set(value) = prefs.edit { putBoolean(KEY_DEFAULTS_NOTED, value) }

    /**
     * The app the mic hands speech to, once the user has picked one. Kept
     * across a change of vault: which apps are installed is a fact about this
     * phone, not about the vault. Read on a mic tap, never by Phase 1.
     */
    var voiceEngine: VoiceEngine?
        get() = VoiceEngine.fromCache(prefs.getString(KEY_VOICE, null))
        set(value) = prefs.edit { putString(KEY_VOICE, value?.toJson()) }

    fun saveVault(uri: Uri, config: VaultConfig) {
        prefs.edit {
            putString(KEY_VAULT, uri.toString())
            putString(KEY_CONFIG, config.toJson())
        }
    }

    /** Forgets the vault and everything read out of it. The voice pick stays; see [voiceEngine]. */
    fun clear() {
        val voice = prefs.getString(KEY_VOICE, null)
        prefs.edit {
            this.clear()
            if (voice != null) putString(KEY_VOICE, voice)
        }
    }

    private companion object {
        const val NAME = "vaultdrop"
        const val KEY_VAULT = "vault_uri"
        const val KEY_CONFIG = "config"
        const val KEY_LINTER = "linter"
        const val KEY_APP = "app"
        const val KEY_TRASH_EXPLAINED = "trash_fallback_explained"
        const val KEY_DEFAULTS_NOTED = "defaults_noted"
        const val KEY_VOICE = "voice_engine"
    }
}
