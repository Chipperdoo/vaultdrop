package dev.vaultdrop.voice

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.speech.RecognizerIntent
import androidx.core.content.pm.PackageInfoCompat
import dev.vaultdrop.core.Hashing
import dev.vaultdrop.core.Transcriber
import dev.vaultdrop.core.VoiceEngine
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
        transcribers = findTranscribers(pm),
        system = recognizers.isNotEmpty(),
        systemLabel = default?.applicationInfo?.loadLabel(pm)?.toString()?.ifBlank { null },
    )
}

/** The action every Open Transcribe service answers, and the name its interface is bound by. */
const val OPEN_TRANSCRIBE_ACTION = "org.opentranscribe.api.ITranscriptionService"

/**
 * Every installed Open Transcribe service, named the way the launcher names
 * its app, with the certificate it is signed with. One the system will not let
 * VaultDrop bind, or whose certificate cannot be read, is left out.
 */
private fun findTranscribers(pm: PackageManager): List<Transcriber> =
    pm.servicesFor(Intent(OPEN_TRANSCRIBE_ACTION)).mapNotNull { info ->
        val service = info.serviceInfo ?: return@mapNotNull null
        if (!service.exported) return@mapNotNull null
        val cert = signingCert(pm, service.packageName) ?: return@mapNotNull null
        Transcriber(
            VoiceEngine.OpenTranscribe(service.packageName, service.name, cert),
            label = info.loadLabel(pm).toString().ifBlank { service.packageName },
        )
    }.sortedBy { it.label.lowercase() }

/**
 * SHA-256 of the certificates [packageName] is signed with, sorted and joined,
 * so an app with more than one signer still has a single value to compare.
 */
private fun signingCert(pm: PackageManager, packageName: String): String? = try {
    PackageInfoCompat.getSignatures(pm, packageName)
        .map { Hashing.sha256(it.toByteArray()) }
        .sorted()
        .joinToString(",")
        .ifEmpty { null }
} catch (_: PackageManager.NameNotFoundException) {
    null
}

private fun PackageManager.servicesFor(intent: Intent): List<ResolveInfo> =
    if (Build.VERSION.SDK_INT >= 33) {
        queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        queryIntentServices(intent, 0)
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
