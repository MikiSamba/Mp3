package com.example.mp3

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

data class Song(
    val id: Long, val uri: Uri, val title: String, val artist: String, val album: String,
    val albumId: Long, val duration: Long, val track: Int, val dateAdded: Long,
    val year: Int = 0, val genre: String = "",
)

fun ContentResolver.loadSongs(): List<Song> {
    val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val cols = arrayOf(
        MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.TRACK, MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.YEAR,
    ) + if (Build.VERSION.SDK_INT >= 30) arrayOf(MediaStore.Audio.Media.GENRE) else emptyArray() // colonna GENRE solo da Android 11
    val out = mutableListOf<Song>()
    runCatching {
        query(base, cols, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null)?.use { c ->
            while (c.moveToNext()) out += Song(
                id = c.getLong(0),
                uri = ContentUris.withAppendedId(base, c.getLong(0)),
                title = c.getString(1) ?: "Senza titolo",
                artist = c.getString(2).let { if (it == null || it == MediaStore.UNKNOWN_STRING) "Artista sconosciuto" else it },
                album = c.getString(3).let { if (it == null || it == MediaStore.UNKNOWN_STRING) "Album sconosciuto" else it },
                albumId = c.getLong(4), duration = c.getLong(5), track = c.getInt(6), dateAdded = c.getLong(7),
                year = c.getInt(8), genre = if (c.columnCount > 9) c.getString(9).orEmpty() else "",
            )
        }
    }
    return out
}

// Cache copertine per album (max 32 MB) + insieme degli album senza copertina, per non ridecodificare.
private val artCache = object : LruCache<Long, Bitmap>(32 * 1024 * 1024) {
    override fun sizeOf(key: Long, value: Bitmap) = value.byteCount
}
private val artMiss = ConcurrentHashMap.newKeySet<Long>()

fun cachedArt(song: Song): Bitmap? = artCache.get(song.albumId)

/** Dopo aver scritto una nuova copertina nel file. */
fun invalidateArt(albumId: Long) { artCache.remove(albumId); artMiss.remove(albumId) }

/** Dati tecnici del file per la finestra "Info file". */
fun fileInfo(ctx: Context, s: Song): List<Pair<String, String>> {
    val rows = mutableListOf("Titolo" to s.title, "Artista" to s.artist, "Album" to s.album)
    if (s.genre.isNotBlank()) rows += "Genere" to s.genre
    if (s.year > 0) rows += "Anno" to s.year.toString()
    if (s.track % 1000 > 0) rows += "Traccia" to (s.track % 1000).toString() // MediaStore: disco*1000 + numero
    rows += "Durata" to "%d:%02d".format(s.duration / 60000, s.duration / 1000 % 60)
    val r = MediaMetadataRetriever()
    runCatching {
        r.setDataSource(ctx, s.uri)
        r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()?.let { rows += "Bitrate" to "${it / 1000} kbps" }
        if (Build.VERSION.SDK_INT >= 31) r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()?.let { rows += "Campionamento" to "${it / 1000f} kHz".replace(".0 ", " ") }
    }
    r.release()
    runCatching {
        ctx.contentResolver.query(s.uri, arrayOf(MediaStore.Audio.Media.MIME_TYPE, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.DATA), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { rows += "Formato" to it }
                rows += "Dimensione" to "%.1f MB".format(c.getLong(1) / 1_048_576.0)
                c.getString(2)?.let { rows += "Percorso" to it }
            }
        }
    }
    return rows
}

fun ContentResolver.albumArt(song: Song): Bitmap? {
    artCache.get(song.albumId)?.let { return it }
    if (song.albumId in artMiss) return null
    val b = runCatching {
        if (Build.VERSION.SDK_INT >= 29) loadThumbnail(song.uri, Size(512, 512), null)
        else openInputStream(ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), song.albumId))
            ?.use { BitmapFactory.decodeStream(it) }
    }.getOrNull()
    if (b != null) artCache.put(song.albumId, b) else artMiss.add(song.albumId)
    return b
}

/** Colore medio della copertina (campionamento a griglia: il ridimensionamento bilineare guarda solo il centro). */
fun Bitmap.averageColor(): Int {
    var r = 0L; var g = 0L; var b = 0L; var n = 0
    val step = maxOf(1, maxOf(width, height) / 32)
    for (y in 0 until height step step) for (x in 0 until width step step) {
        val p = getPixel(x, y)
        r += p shr 16 and 0xFF; g += p shr 8 and 0xFF; b += p and 0xFF; n++
    }
    return (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
}

/** Impostazioni, preferiti, playlist, modifiche ai brani: SharedPreferences esposte come stato Compose. */
class Prefs private constructor(ctx: Context) {
    companion object {
        @Volatile private var instance: Prefs? = null
        fun of(ctx: Context) = instance ?: Prefs(ctx.applicationContext).also { instance = it }
    }

    private val sp = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    private val edits = ctx.getSharedPreferences("edits", Context.MODE_PRIVATE)
    private val lyrics = ctx.getSharedPreferences("lyrics", Context.MODE_PRIVATE)
    private val state = ctx.getSharedPreferences("state", Context.MODE_PRIVATE)

    var theme by pref("theme", "system")       // system | light | dark
    var dynamic by pref("dynamic", false)      // Material You (Android 12+)
    var accent by pref("accent", "Arancio")
    var sort by pref("sort", "title")          // title | artist | album | date
    var showDuration by pref("showDuration", true)
    var shuffle by pref("shuffle", false)
    var repeat by pref("repeat", false)
    var haptics by pref("haptics", true)
    var speed by pref("speed", 1f)
    var eqEnabled by pref("eqEnabled", false)
    var eqPreset by pref("eqPreset", -1)       // -1 = livelli personalizzati
    var eqBands by pref("eqBands", "")         // livelli separati da virgola
    var bassBoost by pref("bassBoost", 0)      // 0..1000
    var crossfade by pref("crossfade", 0)      // secondi, 0 = spenta
    var visualizer by pref("visualizer", false)
    var skipBuild by pref("skipBuild", 0)      // build GitHub di cui l'utente ha rifiutato l'avviso

    var editsVersion by mutableIntStateOf(0); private set

    var favorites: Set<Long> by mutableStateOf(sp.getStringSet("favorites", null).orEmpty().map { it.toLong() }.toSet()); private set
    fun toggleFavorite(id: Long) {
        favorites = if (id in favorites) favorites - id else favorites + id
        sp.edit().putStringSet("favorites", favorites.map(Long::toString).toSet()).apply()
    }

    var recents: List<Long> by mutableStateOf(sp.getString("recents", "")!!.split(',').mapNotNull { it.toLongOrNull() }); private set
    fun addRecent(id: Long) {
        recents = (listOf(id) + recents.filter { it != id }).take(50)
        sp.edit().putString("recents", recents.joinToString(",")).apply()
    }

    var playlists: Map<String, List<Long>> by mutableStateOf(loadPlaylists()); private set
    private fun loadPlaylists(): Map<String, List<Long>> {
        val o = runCatching { JSONObject(sp.getString("playlists", "{}")!!) }.getOrDefault(JSONObject())
        return o.keys().asSequence().associateWith { k -> val a = o.getJSONArray(k); List(a.length()) { a.getLong(it) } }
    }
    /** ids == null elimina la playlist. */
    fun setPlaylist(name: String, ids: List<Long>?) {
        playlists = if (ids == null) playlists - name else playlists + (name to ids)
        sp.edit().putString("playlists", JSONObject(playlists.mapValues { JSONArray(it.value) }).toString()).apply()
    }

    /** Stato per "riprendi dove eri". */
    fun saveState(currentId: Long?, position: Int, queue: List<Long>, upNext: List<Long>, queueName: String) {
        state.edit().putString("s", JSONObject().put("current", currentId ?: -1).put("pos", position)
            .put("queue", JSONArray(queue)).put("upNext", JSONArray(upNext)).put("name", queueName).toString()).apply()
    }
    fun loadState(): JSONObject? = state.getString("s", null)?.let { runCatching { JSONObject(it) }.getOrNull() }

    // ponytail: le modifiche restano nell'app (MediaStore non permette di cambiare i tag su Android 10+).
    // Per scrivere i tag nel file serve una libreria ID3 + MediaStore.createWriteRequest.
    fun applyEdits(s: Song): Song {
        val o = JSONObject(edits.getString(s.id.toString(), null) ?: return s)
        return s.copy(title = o.optString("title", s.title), artist = o.optString("artist", s.artist), album = o.optString("album", s.album))
    }

    /** title == null → ripristina i valori originali. */
    fun saveEdit(id: Long, title: String?, artist: String?, album: String?) {
        edits.edit().apply {
            if (title == null) remove(id.toString())
            else putString(id.toString(), JSONObject().put("title", title).put("artist", artist).put("album", album).toString())
        }.apply()
        editsVersion++
    }

    fun lyrics(id: Long): String? = lyrics.getString(id.toString(), null)
    fun saveLyrics(id: Long, text: String) = lyrics.edit().putString(id.toString(), text).apply()

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> pref(key: String, def: T) = object : ReadWriteProperty<Any?, T> {
        private var state by mutableStateOf(sp.all[key] as? T ?: def)
        override fun getValue(thisRef: Any?, property: KProperty<*>) = state
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            state = value
            sp.edit().apply {
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is String -> putString(key, value)
                    is Int -> putInt(key, value)
                    is Float -> putFloat(key, value)
                    else -> error("tipo non supportato: $value")
                }
            }.apply()
        }
    }
}
