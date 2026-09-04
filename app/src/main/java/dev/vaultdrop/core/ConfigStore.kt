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

    fun saveVault(uri: Uri, config: VaultConfig) {
        prefs.edit {
            putString(KEY_VAULT, uri.toString())
            putString(KEY_CONFIG, config.toJson())
        }
    }

    fun clear() {
        prefs.edit { this.clear() }
    }

    /** True on the very first launch, and after the vault grant is revoked. */
    val isConfigured: Boolean get() = vaultUri != null && loadConfig() != null

    private companion object {
        const val NAME = "vaultdrop"
        const val KEY_VAULT = "vault_uri"
        const val KEY_CONFIG = "config"
        const val KEY_LINTER = "linter"
        const val KEY_APP = "app"
        const val KEY_TRASH_EXPLAINED = "trash_fallback_explained"
    }
}
