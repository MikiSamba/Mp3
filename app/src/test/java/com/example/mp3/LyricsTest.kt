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

class TagsTest {
    private fun frame(id: String, body: ByteArray): ByteArray {
        val n = body.size
        return id.toByteArray(Charsets.ISO_8859_1) + byteArrayOf((n shr 24).toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte()) + byteArrayOf(0, 0) + body
    }
    private fun v23(frames: ByteArray): ByteArray {
        val n = frames.size
        return byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0,
            (n shr 21 and 0x7F).toByte(), (n shr 14 and 0x7F).toByte(), (n shr 7 and 0x7F).toByte(), (n and 0x7F).toByte()) + frames
    }
    private fun textOf(data: ByteArray, id: String): String? {
        val size = syncsafe(data, 6); var p = 10
        while (p + 10 <= 10 + size && data[p] != 0.toByte()) {
            val fid = String(data, p, 4, Charsets.ISO_8859_1); val len = syncsafe(data, p + 4)
            if (fid == id) return String(data, p + 11, len - 1, Charsets.UTF_8)
            p += 10 + len
        }
        return null
    }

    @Test fun `rewrite keeps lyrics and audio and replaces title`() {
        val audio = ByteArray(300) { (it % 251).toByte() }
        val src = v23(frame("TIT2", byteArrayOf(3) + "Vecchio".toByteArray()) + frame("USLT", byteArrayOf(3) + "ita".toByteArray() + byteArrayOf(0) + "Testo".toByteArray())) + audio
        val out = rewriteId3(src, "Nuovo", "Artista", "Album")!!
        assertEquals(4, out[3].toInt())
        assertEquals("Nuovo", textOf(out, "TIT2"))
        assertEquals("Artista", textOf(out, "TPE1"))
        assertEquals("Album", textOf(out, "TALB"))
        assertEquals("Testo", parseUslt(java.io.ByteArrayInputStream(out)))
        assertEquals(audio.toList(), out.copyOfRange(out.size - audio.size, out.size).toList())
    }

    @Test fun `file without tag gets a new one`() {
        val audio = ByteArray(50) { 7 }
        val out = rewriteId3(audio, "T", "A", "B")!!
        assertEquals("T", textOf(out, "TIT2"))
        assertEquals(audio.toList(), out.copyOfRange(out.size - 50, out.size).toList())
    }
}
