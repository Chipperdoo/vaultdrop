package dev.vaultdrop.voice

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.speech.RecognizerIntent
import dev.vaultdrop.core.VoiceOptions

/**
 * What on this phone can take speech right now.
 *
 * Every line of this is a binder call into the package manager, so it runs
 * when the mic is tapped, never at launch, and off the main thread.
 */
fun findVoiceOptions(context: Context): VoiceOptions {
    val pm = context.packageManager
    val speech = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
    val recognizers = pm.activitiesFor(speech)
    // With several recognisers and no default, this resolves to Android's own
    // chooser, which is none of them and has no name worth showing.
    val default = pm.defaultActivityFor(speech)?.activityInfo?.takeIf { chosen ->
        recognizers.any { it.activityInfo.packageName == chosen.packageName && it.activityInfo.name == chosen.name }
    }
    return VoiceOptions(
        system = recognizers.isNotEmpty(),
        systemLabel = default?.applicationInfo?.loadLabel(pm)?.toString()?.ifBlank { null },
    )
}

private fun PackageManager.activitiesFor(intent: Intent): List<ResolveInfo> =
    if (Build.VERSION.SDK_INT >= 33) {
        queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
    } else {
        @Suppress("DEPRECATION")
        queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
    }

private fun PackageManager.defaultActivityFor(intent: Intent): ResolveInfo? =
    if (Build.VERSION.SDK_INT >= 33) {
        resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
    } else {
        @Suppress("DEPRECATION")
        resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    }
