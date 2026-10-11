package dev.vaultdrop.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceTest {

    private val scrib = VoiceEngine.OpenTranscribe("org.scrib.transcriber", "org.scrib.transcriber.TranscriptionService", "aa11")
    private val other = VoiceEngine.OpenTranscribe("org.example.whisper", "org.example.whisper.Service", "bb22")

    private fun installed(vararg engines: VoiceEngine.OpenTranscribe, system: Boolean = true) =
        VoiceOptions(transcribers = engines.map { Transcriber(it, it.packageName) }, system = system)

    // ── Choosing ─────────────────────────────────────────────────────────────

    @Test
    fun `system voice input alone is used without a question`() {
        assertEquals(VoiceChoice.Use(VoiceEngine.System), chooseVoice(installed(), stored = null))
    }

    @Test
    fun `nothing installed is nothing to use`() {
        assertEquals(VoiceChoice.Unavailable, chooseVoice(installed(system = false), stored = null))
        assertEquals(VoiceChoice.Unavailable, chooseVoice(installed(system = false), stored = VoiceEngine.System))
        assertEquals(VoiceChoice.Unavailable, chooseVoice(installed(system = false), stored = scrib))
    }

    @Test
    fun `an Open Transcribe app asks the first time, even when it is the only way in`() {
        assertEquals(VoiceChoice.Ask(AskReason.FirstTime), chooseVoice(installed(scrib), stored = null))
        assertEquals(VoiceChoice.Ask(AskReason.FirstTime), chooseVoice(installed(scrib, system = false), stored = null))
    }

    @Test
    fun `a remembered pick is used while it is still there`() {
        assertEquals(VoiceChoice.Use(scrib), chooseVoice(installed(scrib, other), stored = scrib))
        assertEquals(VoiceChoice.Use(VoiceEngine.System), chooseVoice(installed(scrib), stored = VoiceEngine.System))
    }

    @Test
    fun `a pick signed by someone else now asks again`() {
        val resigned = scrib.copy(certSha256 = "ff00")
        assertEquals(VoiceChoice.Ask(AskReason.Changed), chooseVoice(installed(resigned), stored = scrib))
        assertEquals(
            VoiceChoice.Ask(AskReason.Changed),
            chooseVoice(installed(resigned, system = false), stored = scrib),
        )
    }

    @Test
    fun `a pick that has gone asks again when there is still a choice to make`() {
        assertEquals(VoiceChoice.Ask(AskReason.Gone), chooseVoice(installed(other), stored = scrib))
        assertEquals(VoiceChoice.Ask(AskReason.Gone), chooseVoice(installed(scrib, system = false), stored = VoiceEngine.System))
    }

    @Test
    fun `a pick that has gone leaves system voice input to answer when it is all there is`() {
        assertEquals(VoiceChoice.Use(VoiceEngine.System), chooseVoice(installed(), stored = scrib))
    }

    @Test
    fun `another class in the same package is not the same service`() {
        val moved = scrib.copy(className = "org.scrib.transcriber.Other")
        assertEquals(VoiceChoice.Ask(AskReason.Gone), chooseVoice(installed(moved), stored = scrib))
    }

    // ── Remembering ──────────────────────────────────────────────────────────

    @Test
    fun `both kinds of engine survive the cache`() {
        assertEquals(VoiceEngine.System, VoiceEngine.fromCache(VoiceEngine.System.toJson()))
        assertEquals(scrib, VoiceEngine.fromCache(scrib.toJson()))
    }

    @Test
    fun `a cache this version cannot read is no pick at all`() {
        assertNull(VoiceEngine.fromCache(null))
        assertNull(VoiceEngine.fromCache("not json"))
        assertNull(VoiceEngine.fromCache("""{"kind":"speech-recognizer"}"""))
        assertNull(VoiceEngine.fromCache("""{"kind":"open-transcribe","package":"org.scrib.transcriber"}"""))
    }

    // ── Transcripts ──────────────────────────────────────────────────────────

    @Test
    fun `Whisper's leading space is dropped`() {
        assertEquals("Went to the shop.", dictationText(" Went to the shop.", next = null))
    }

    @Test
    fun `runs of spaces inside a line close up`() {
        assertEquals("Went to the shop.", dictationText("Went  to\tthe  shop. ", next = null))
    }

    @Test
    fun `paragraph breaks stay, and no more than one blank line`() {
        assertEquals("First thought.\n\nSecond one.", dictationText(" First thought.\n\n Second one.", next = null))
        assertEquals("One.\n\nTwo.", dictationText("One.\n\n\n\nTwo.", next = null))
        assertEquals("One.\n\nTwo.", dictationText("One. \n \n Two.", next = null))
    }

    @Test
    fun `nothing heard is nothing to insert`() {
        assertEquals("", dictationText("", next = 'a'))
        assertEquals("", dictationText("  \n ", next = 'a'))
    }

    @Test
    fun `a transcript in front of a word gets a space after it`() {
        assertEquals("to the shop ", dictationText("to the shop", next = 'a'))
        assertEquals("to the shop ", dictationText("to the shop", next = '3'))
    }

    @Test
    fun `a transcript before punctuation, a line end or nothing gets no space after it`() {
        assertEquals("to the shop", dictationText("to the shop", next = '.'))
        assertEquals("to the shop", dictationText("to the shop", next = '\n'))
        assertEquals("to the shop", dictationText("to the shop", next = ' '))
        assertEquals("to the shop", dictationText("to the shop", next = null))
    }

    @Test
    fun `the tidied transcript joins prose the way splice expects`() {
        val text = "went"
        val edit = splice(text, text.length, dictationText(" to the shop.", next = null), block = false)
        assertEquals("went to the shop.", text.substring(0, edit.start) + edit.text + text.substring(edit.end))
    }
}
