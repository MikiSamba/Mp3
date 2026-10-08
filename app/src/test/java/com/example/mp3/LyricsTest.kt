package com.example.mp3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream

class LyricsTest {
    private fun id3(version: Int, frames: ByteArray): ByteArray {
        val size = frames.size
        val header = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), version.toByte(), 0, 0,
            (size shr 21 and 0x7F).toByte(), (size shr 14 and 0x7F).toByte(), (size shr 7 and 0x7F).toByte(), (size and 0x7F).toByte())
        return header + frames + ByteArray(64) // padding + "audio"
    }

    private fun frame(id: String, body: ByteArray, v4: Boolean): ByteArray {
        val n = body.size
        val len = if (v4) byteArrayOf((n shr 21 and 0x7F).toByte(), (n shr 14 and 0x7F).toByte(), (n shr 7 and 0x7F).toByte(), (n and 0x7F).toByte())
                  else byteArrayOf((n shr 24).toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte())
        return id.toByteArray(Charsets.ISO_8859_1) + len + byteArrayOf(0, 0) + body
    }

    @Test fun `USLT utf8 in ID3v2 3 after another frame`() {
        val tit2 = frame("TIT2", byteArrayOf(3) + "Titolo".toByteArray(), v4 = false)
        val uslt = frame("USLT", byteArrayOf(3) + "ita".toByteArray() + byteArrayOf(0) + "Riga 1\nRiga 2".toByteArray(), v4 = false)
        assertEquals("Riga 1\nRiga 2", parseUslt(ByteArrayInputStream(id3(3, tit2 + uslt))))
    }

    @Test fun `USLT utf16 with BOM in ID3v2 4`() {
        val bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        val desc = bom + byteArrayOf(0, 0)
        val text = bom + "Ciao".toByteArray(Charsets.UTF_16LE)
        val uslt = frame("USLT", byteArrayOf(1) + "eng".toByteArray() + desc + text, v4 = true)
        assertEquals("Ciao", parseUslt(ByteArrayInputStream(id3(4, uslt))))
    }

    @Test fun `no tag or no USLT gives null`() {
        assertNull(parseUslt(ByteArrayInputStream(ByteArray(100))))
        assertNull(parseUslt(ByteArrayInputStream(id3(3, frame("TIT2", byteArrayOf(3) + "x".toByteArray(), v4 = false)))))
    }

    @Test fun `LRC parsing sorts by time and drops metadata lines`() {
        val lines = parseLrc("[ar:Qualcuno]\n[00:10.50]seconda\n[00:01.00][00:20.00]prima e terza\n")
        assertEquals(listOf(1000L, 10500L, 20000L), lines.map { it.time })
        assertEquals(listOf("prima e terza", "seconda", "prima e terza"), lines.map { it.text })
        assertNull(parseLrc("solo testo\nsemplice")[0].time)
    }
}
