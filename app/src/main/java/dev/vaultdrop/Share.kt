package dev.vaultdrop

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import dev.vaultdrop.core.Sharing

/**
 * What another app shared into VaultDrop: text, files, or both.
 *
 * Read off the intent's extras and nothing else. The extras are already in
 * memory, so this costs nothing ahead of the first frame. A file's name, its
 * type and its bytes all come from the sender's provider, and they are read
 * once the editor is up; see [EditorViewModel.receiveShare].
 */
data class Share(
    val text: String?,
    val files: List<Uri>,
    /** The intent's own type, often a wildcard, for a file whose provider will not say. */
    val type: String?,
) {
    companion object {

        /**
         * The share [intent] carries, or null if it is not one.
         *
         * Opening VaultDrop from Recents after a share hands back that same
         * intent, marked as coming from history, and the text would go in a
         * second time. That one is not a share.
         */
        fun from(intent: Intent?): Share? {
            if (intent == null) return null
            if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null
            val multiple = when (intent.action) {
                Intent.ACTION_SEND -> false
                Intent.ACTION_SEND_MULTIPLE -> true
                else -> return null
            }
            // The extras come from another app. One that will not unparcel is
            // no share at all, rather than a crash before the first frame.
            return runCatching { read(intent, multiple) }.getOrNull()
        }

        private fun read(intent: Intent, multiple: Boolean): Share? {
            val files = if (multiple) {
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            } else {
                listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            }
            // SEND_MULTIPLE may carry one text per item. Each is its own
            // paragraph, as they would be shared one at a time.
            val raw = if (multiple) {
                intent.getCharSequenceArrayListExtra(Intent.EXTRA_TEXT)?.mapNotNull { Sharing.cleanText(it) }
                    ?.joinToString("\n\n")
                    ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            } else {
                intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            }
            val text = Sharing.cleanText(raw)
            if (text == null && files.isEmpty()) return null
            return Share(text, files, intent.type)
        }
    }
}
