package com.example.mp3

import java.io.ByteArrayOutputStream

/**
 * Modifiche da scrivere nel tag: [text] = frame di testo (TIT2, TPE1, TALB, TCON, TDRC, TRCK...; valore vuoto = frame rimosso),
 * [lyrics] = testo (USLT), [cover] = copertina (APIC). null = lascia com'è.
 */
class TagEdit(val text: Map<String, String> = emptyMap(), val lyrics: String? = null, val cover: ByteArray? = null, val coverMime: String = "image/jpeg")

fun rewriteId3(data: ByteArray, title: String, artist: String, album: String): ByteArray? =
    rewriteId3(data, TagEdit(mapOf("TIT2" to title, "TPE1" to artist, "TALB" to album)))

/**
 * Riscrive i frame indicati nel tag ID3v2 di un MP3 conservando gli altri. Produce un tag ID3v2.4 (UTF-8).
 * null = tag non gestito (ID3v2.2 o unsynchronisation).
 */
// ponytail: i flag dei frame originali vengono azzerati; frame compressi o con unsync per-frame (rarissimi) andrebbero decodificati.
fun rewriteId3(data: ByteArray, edit: TagEdit): ByteArray? {
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
    val utf8 = byteArrayOf(3)
    val replaced = edit.text.keys.toMutableSet()
    if ("TDRC" in replaced) replaced += "TYER" // anno: in v2.4 è TDRC, in v2.3 era TYER
    if (edit.lyrics != null) replaced += "USLT"
    if (edit.cover != null) replaced += "APIC"
    val fresh = mutableListOf<Pair<String, ByteArray>>()
    for ((id, v) in edit.text) if (v.isNotBlank()) fresh += id to (utf8 + v.toByteArray(Charsets.UTF_8))
    // Android legge l'anno solo dal vecchio TYER (non da TDRC): scritti entrambi, così MediaStore lo vede.
    edit.text["TDRC"]?.take(4)?.takeIf { it.isNotBlank() }?.let { fresh += "TYER" to (utf8 + it.toByteArray(Charsets.UTF_8)) }
    edit.lyrics?.let { fresh += "USLT" to (utf8 + "ita".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0) + it.toByteArray(Charsets.UTF_8)) }
    edit.cover?.let { fresh += "APIC" to (utf8 + edit.coverMime.toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0, 3, 0) + it) } // tipo 3 = copertina frontale
    val all = fresh + frames.filter { it.first !in replaced }
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
