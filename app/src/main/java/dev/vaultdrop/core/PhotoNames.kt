package dev.vaultdrop.core

import java.time.LocalDateTime
import java.util.Locale

/**
 * What a photo is called once it is in the vault, and whether Obsidian can
 * draw it there.
 */
object PhotoNames {

    /**
     * The word in front of the timestamp on a photo VaultDrop names itself.
     *
     * It copies the shape of Obsidian's own `Recording` and `Pasted image`
     * names. Whether to use `Pasted image` instead, so every image in the vault
     * looks alike, is open question 3 in docs/plans/multimedia.md. Change it
     * here and nowhere else.
     */
    const val STEM_PREFIX = "Photo"

    /** What a camera app answering `ACTION_IMAGE_CAPTURE` writes: a JPEG. */
    const val CAMERA_EXT = "jpg"

    /**
     * The Moment pattern Obsidian stamps its `Recording` and `Pasted image`
     * files with. It goes through [MomentFormat] so it gets the same English
     * locale as every other filename the app writes.
     */
    private const val STAMP = "YYYYMMDDHHmmss"

    /** `Photo 20260929174501`. A camera photo's stem, and the stem of a picked one with no name. */
    fun stamped(now: LocalDateTime): String = "$STEM_PREFIX ${MomentFormat.formatter(STAMP).format(now)}"

    data class Name(val stem: String, val ext: String)

    /**
     * The name a picked photo keeps, split into stem and extension. Obsidian
     * keeps a file's own name when you attach it, so this does too.
     *
     * [displayName] is what the photo's provider calls it. [mimeExt] is the
     * extension its MIME type maps to, for a name that has none. A name with
     * nothing usable left once [Attachments.cleanName] is done gets [stamped].
     *
     * The last dot only splits off an extension when what follows looks like
     * one: one to five letters and digits, at least one of them a letter. So
     * `Scan 2026.01.31` is a stem with no extension, not a `.31` file. A
     * trailing dot goes, or `IMG.` would come out as `IMG..jpg`.
     */
    fun picked(displayName: String?, mimeExt: String?, now: LocalDateTime): Name {
        val name = displayName?.trim()?.trimEnd('.').orEmpty()
        val dot = name.lastIndexOf('.')
        val own = if (dot >= 0) name.substring(dot + 1) else ""
        val split = isExtension(own)
        return Name(
            stem = Attachments.cleanName(if (split) name.substring(0, dot) else name) ?: stamped(now),
            ext = if (split) own else mimeExt?.takeIf(::isExtension).orEmpty(),
        )
    }

    private fun isExtension(s: String): Boolean =
        s.length in 1..5 && s.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' } && s.any { it.isLetter() }

    /**
     * The image formats Obsidian draws in a note, as listed at
     * https://obsidian.md/help/file-formats. HEIC is not one of them. A photo
     * in any other format is still copied in, and the notice says Obsidian
     * will not show it.
     */
    val RENDERED: Set<String> = setOf("avif", "bmp", "gif", "jpeg", "jpg", "png", "svg", "webp")

    fun rendersInObsidian(ext: String): Boolean = ext.lowercase(Locale.ROOT) in RENDERED
}
