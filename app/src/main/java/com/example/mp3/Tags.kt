package com.example.mp3

import java.io.ByteArrayOutputStream

/**
 * Riscrive titolo/artista/album nel tag ID3v2 di un MP3 conservando gli altri frame (copertina, testo...).
 * Produce un tag ID3v2.4 (UTF-8). null = tag non gestito (ID3v2.2 o unsynchronisation).
 */
// ponytail: i flag dei frame originali vengono azzerati; frame compressi o con unsync per-frame (rarissimi) andrebbero decodificati.
fun rewriteId3(data: ByteArray, title: String, artist: String, album: String): ByteArray? {
    var audioStart = 0
    val frames = mutableListOf<Pair<String, ByteArray>>()
    if (data.size >= 10 && String(data, 0, 3, Charsets.ISO_8859_1) == "ID3") {
        val ver = data[3].toInt()
        if (ver !in 3..4 || data[5].toInt() and 0x80 != 0) return null
        val v4 = ver == 4
        val end = minOf(10 + syncsafe(data, 6), data.size)
        audioStart = end + if (v4 && data[5].toInt() and 0x10 != 0) 10 else 0
        var p = 10
        if (data[5].toInt() and 0x40 != 0) p += if (v4) syncsafe(data, 10) else int32(data, 10) + 4
        while (p + 10 <= end) {
            if (data[p] == 0.toByte()) break
            val id = String(data, p, 4, Charsets.ISO_8859_1)
            val len = if (v4) syncsafe(data, p + 4) else int32(data, p + 4)
            if (len < 0 || p + 10 + len > end) break
            frames += id to data.copyOfRange(p + 10, p + 10 + len)
            p += 10 + len
        }
    }
    fun text(id: String, s: String) = id to (byteArrayOf(3) + s.toByteArray(Charsets.UTF_8))
    val all = listOf(text("TIT2", title), text("TPE1", artist), text("TALB", album)) +
        frames.filter { it.first !in setOf("TIT2", "TPE1", "TALB") }
    val body = ByteArrayOutputStream()
    for ((id, b) in all) {
        body.write(id.toByteArray(Charsets.ISO_8859_1))
        body.write(syncsafeBytes(b.size))
        body.write(0); body.write(0)
        body.write(b)
    }
    val padding = 1024
    val header = "ID3".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(4, 0, 0) + syncsafeBytes(body.size() + padding)
    return header + body.toByteArray() + ByteArray(padding) + data.copyOfRange(audioStart, data.size)
}

private fun syncsafeBytes(n: Int) = byteArrayOf((n shr 21 and 0x7F).toByte(), (n shr 14 and 0x7F).toByte(), (n shr 7 and 0x7F).toByte(), (n and 0x7F).toByte())
