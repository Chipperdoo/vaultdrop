package dev.vaultdrop.core

import org.json.JSONObject

/**
 * What the mic button hands speech to.
 *
 * VaultDrop recognises no speech itself. Either Android's own voice input
 * answers, through whichever app the phone has set up for it, or an app that
 * speaks Scrib's Open Transcribe contract does.
 */
sealed interface VoiceEngine {

    /** `RECOGNIZE_SPEECH`. Android picks the app and shows its window. */
    data object System : VoiceEngine

    /**
     * An Open Transcribe service, and the certificate it was signed with when
     * the user picked it. VaultDrop hands this app the microphone's audio, so a
     * different app turning up under the same name has to be asked about again.
     */
    data class OpenTranscribe(
        val packageName: String,
        val className: String,
        val certSha256: String,
    ) : VoiceEngine {
        /** The same service, whoever signed it. */
        fun sameService(other: OpenTranscribe): Boolean =
            packageName == other.packageName && className == other.className
    }

    fun toJson(): String = when (this) {
        System -> JSONObject().put("kind", KIND_SYSTEM)
        is OpenTranscribe -> JSONObject()
            .put("kind", KIND_OPEN_TRANSCRIBE)
            .put("package", packageName)
            .put("class", className)
            .put("cert", certSha256)
    }.toString()

    companion object {
        private const val KIND_SYSTEM = "system"
        private const val KIND_OPEN_TRANSCRIBE = "open-transcribe"

        /** Null for anything this version cannot read, which asks again rather than guessing. */
        fun fromCache(json: String?): VoiceEngine? {
            if (json == null) return null
            val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
            return when (o.optString("kind")) {
                KIND_SYSTEM -> System
                KIND_OPEN_TRANSCRIBE -> {
                    val pkg = o.optString("package")
                    val cls = o.optString("class")
                    val cert = o.optString("cert")
                    if (pkg.isEmpty() || cls.isEmpty() || cert.isEmpty()) null
                    else OpenTranscribe(pkg, cls, cert)
                }
                else -> null
            }
        }
    }
}

/**
 * What a request to an Open Transcribe engine reports: any number of
 * [Progress], then exactly one [Final] or [Failure]. The contract promises the
 * one terminal call, and the client in `voice/` keeps that promise on its own
 * side too, when it gives up or the engine dies.
 */
sealed interface VoiceEvent {
    /** Everything heard so far. Cumulative, and it only ever grows. */
    data class Progress(val text: String) : VoiceEvent

    /** The whole transcript. */
    data class Final(val text: String) : VoiceEvent

    data class Failure(val reason: VoiceFailure) : VoiceEvent
}

/** Why a request ended without a transcript. */
sealed interface VoiceFailure {
    /** The user cancelled. Not worth a notice. */
    data object Cancelled : VoiceFailure

    /** The engine has no speech model yet, and says so in its capabilities or its error. */
    data object ModelNotReady : VoiceFailure

    /** The engine turned the request down. Scrib does while its benchmark runs. */
    data object Busy : VoiceFailure

    /** Android would not bind to the engine at all. */
    data object Unreachable : VoiceFailure

    /** The engine went away part way through. */
    data object Died : VoiceFailure

    /** The engine went quiet for longer than [IdleTimeout] allows. */
    data object TimedOut : VoiceFailure

    /** The microphone would not start, or stopped giving audio. */
    data object Microphone : VoiceFailure

    /** The engine's own error, in its own words when it gave any. */
    data class Engine(val message: String?) : VoiceFailure
}

/** An Open Transcribe service installed on the phone, as of the tap that looked. */
data class Transcriber(val engine: VoiceEngine.OpenTranscribe, val label: String)

/**
 * Everything that could take speech right now. Built from package queries, so
 * only ever on a tap.
 *
 * [systemLabel] names the app Android would open for `RECOGNIZE_SPEECH`, when
 * the user has set a default or only one app answers. With several and no
 * default, Android shows its own chooser and there is no one name to give.
 */
data class VoiceOptions(
    val transcribers: List<Transcriber> = emptyList(),
    val system: Boolean = false,
    val systemLabel: String? = null,
)

/** Why the mic is asking which app to use. */
enum class AskReason {
    /** Nothing picked yet, and there is more than one way to go. */
    FirstTime,

    /** The app picked last time is not installed any more. */
    Gone,

    /** The app picked last time is signed by someone else now. */
    Changed,
}

/** What a mic tap does, given what is installed and what was picked before. */
sealed interface VoiceChoice {
    data class Use(val engine: VoiceEngine) : VoiceChoice
    data class Ask(val reason: AskReason) : VoiceChoice

    /** Nothing on the phone can take speech. */
    data object Unavailable : VoiceChoice
}

/**
 * The one-button rule.
 *
 * System voice input on its own needs no question: Android already asks which
 * app answers it, and remembers. An Open Transcribe app is another matter. The
 * contract forbids binding to whatever claims the action, because it gets the
 * user's audio, so the first tap asks, and so does any tap after the app picked
 * has gone or changed hands. A remembered pick stands while it is still there.
 *
 * Only system voice input left, after the picked app went: that is used
 * without a question, the same as if it had always been the only one.
 */
fun chooseVoice(options: VoiceOptions, stored: VoiceEngine?): VoiceChoice {
    when (stored) {
        VoiceEngine.System -> if (options.system) return VoiceChoice.Use(VoiceEngine.System)
        is VoiceEngine.OpenTranscribe -> {
            val now = options.transcribers.firstOrNull { it.engine.sameService(stored) }
            if (now != null) {
                return if (now.engine.certSha256 == stored.certSha256) VoiceChoice.Use(stored)
                else VoiceChoice.Ask(AskReason.Changed)
            }
        }
        null -> {}
    }
    val reason = if (stored == null) AskReason.FirstTime else AskReason.Gone
    return when {
        options.transcribers.isNotEmpty() -> VoiceChoice.Ask(reason)
        options.system -> VoiceChoice.Use(VoiceEngine.System)
        else -> VoiceChoice.Unavailable
    }
}

/**
 * A transcript, ready to splice into the note as prose.
 *
 * Whisper puts a space in front of every segment, so the text arrives with one
 * at the start, and [splice] would keep it. Ends are trimmed, and a run of
 * spaces inside a line closes up to one. Blank lines between paragraphs stay,
 * since Scrib uses them to mark a long pause and Markdown reads them as one.
 *
 * [next] is the character after the caret. A transcript dropped in front of a
 * word gets a space after it, so the two do not run together. Punctuation and
 * line ends are left alone.
 */
fun dictationText(raw: String, next: Char?): String {
    val lines = raw.trim().lines().map { line -> line.trim().replace(SPACES, " ") }
    val text = lines.joinToString("\n").replace(BLANK_RUN, "\n\n")
    if (text.isEmpty()) return ""
    return if (next != null && next.isLetterOrDigit()) "$text " else text
}

private val SPACES = Regex("[ \\t\\u00A0]+")
private val BLANK_RUN = Regex("\\n{3,}")
