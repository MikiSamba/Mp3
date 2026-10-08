package com.example.mp3

import android.content.ContentResolver
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Line(val time: Long?, val text: String)

/** Righe LRC "[mm:ss.xx]testo"; se nessuna riga ha il tempo, testo semplice. */
fun parseLrc(raw: String): List<Line> {
    val re = Regex("""\[(\d+):(\d+(?:\.\d+)?)]""")
    val lines = raw.lines().flatMap { l ->
        val stamps = re.findAll(l).toList()
        if (stamps.isEmpty()) listOf(Line(null, l.trim()))
        else stamps.map { m -> Line(m.groupValues[1].toLong() * 60000 + (m.groupValues[2].toDouble() * 1000).toLong(), re.replace(l, "").trim()) }
    }
    return if (lines.any { it.time != null }) lines.filter { it.time != null }.sortedBy { it.time } else lines
}

/** Testo incorporato nel tag ID3v2 (frame USLT) di un MP3. */
fun ContentResolver.embeddedLyrics(uri: Uri): String? = runCatching { openInputStream(uri)?.use(::parseUslt) }.getOrNull()

// ponytail: solo ID3v2.3/2.4 senza unsynchronisation; FLAC/OGG/M4A passano alla ricerca online.
internal fun parseUslt(s: InputStream): String? {
    val h = ByteArray(10)
    if (s.read(h) < 10 || String(h, 0, 3, Charsets.ISO_8859_1) != "ID3" || h[3].toInt() < 3) return null
    val v4 = h[3].toInt() == 4
    val size = syncsafe(h, 6)
    val t = ByteArray(size)
    var got = 0
    while (got < size) { val r = s.read(t, got, size - got); if (r < 0) break; got += r }
    var p = 0
    if (h[5].toInt() and 0x40 != 0) p += if (v4) syncsafe(t, 0) else int32(t, 0) + 4
    while (p + 10 <= got) {
        if (t[p] == 0.toByte()) break
        val id = String(t, p, 4, Charsets.ISO_8859_1)
        val len = if (v4) syncsafe(t, p + 4) else int32(t, p + 4)
        val d = p + 10
        if (len < 0 || d + len > got) break
        if (id == "USLT") {
            val enc = t[d].toInt()
            val cs = when (enc) { 1 -> Charsets.UTF_16; 2 -> Charsets.UTF_16BE; 3 -> Charsets.UTF_8; else -> Charsets.ISO_8859_1 }
            val end = d + len
            var q = d + 4 // salta encoding + lingua, poi il descrittore terminato da null
            if (enc == 1 || enc == 2) { while (q + 1 < end && (t[q] != 0.toByte() || t[q + 1] != 0.toByte())) q += 2; q += 2 }
            else { while (q < end && t[q] != 0.toByte()) q++; q++ }
            if (q >= end) return null
            return String(t, q, end - q, cs).trim('\u0000', ' ', '\n', '\r').ifEmpty { null }
        }
        p = d + len
    }
    return null
}

internal fun syncsafe(b: ByteArray, o: Int) =
    ((b[o].toInt() and 0x7F) shl 21) or ((b[o + 1].toInt() and 0x7F) shl 14) or ((b[o + 2].toInt() and 0x7F) shl 7) or (b[o + 3].toInt() and 0x7F)

internal fun int32(b: ByteArray, o: Int) =
    ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)

/** Cerca il testo su lrclib.net (gratuito, senza chiave). Preferisce quello sincronizzato. */
// ponytail: nessun retry; un errore di rete = "non disponibile" finché non si riapre il testo.
fun fetchLyrics(song: Song): String? = runCatching {
    fun get(path: String): String? {
        val c = URL("https://lrclib.net/api/$path").openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 10_000
        c.setRequestProperty("User-Agent", "Musica/1.3 (https://github.com/MikiSamba/Mp3)")
        return if (c.responseCode == 200) c.inputStream.bufferedReader().readText() else null
    }
    val q = "track_name=${enc(song.title)}&artist_name=${enc(song.artist)}"
    val obj = get("get?$q&album_name=${enc(song.album)}&duration=${song.duration / 1000}")?.let(::JSONObject)
        ?: get("search?$q")?.let { JSONArray(it).optJSONObject(0) }
        ?: return null
    listOf("syncedLyrics", "plainLyrics").firstNotNullOfOrNull { k -> obj.optString(k).takeIf { it.isNotBlank() && it != "null" } }
}.getOrNull()

private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
