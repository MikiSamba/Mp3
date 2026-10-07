package com.example.mp3

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Size
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

data class Song(
    val id: Long, val uri: Uri, val title: String, val artist: String, val album: String,
    val albumId: Long, val duration: Long, val track: Int, val dateAdded: Long,
)

fun ContentResolver.loadSongs(): List<Song> {
    val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val cols = arrayOf(
        MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.TRACK, MediaStore.Audio.Media.DATE_ADDED,
    )
    val out = mutableListOf<Song>()
    query(base, cols, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null)?.use { c ->
        while (c.moveToNext()) out += Song(
            id = c.getLong(0),
            uri = ContentUris.withAppendedId(base, c.getLong(0)),
            title = c.getString(1) ?: "Senza titolo",
            artist = c.getString(2).let { if (it == null || it == MediaStore.UNKNOWN_STRING) "Artista sconosciuto" else it },
            album = c.getString(3).let { if (it == null || it == MediaStore.UNKNOWN_STRING) "Album sconosciuto" else it },
            albumId = c.getLong(4), duration = c.getLong(5), track = c.getInt(6), dateAdded = c.getLong(7),
        )
    }
    return out
}

fun ContentResolver.albumArt(song: Song): Bitmap? = runCatching {
    if (Build.VERSION.SDK_INT >= 29) loadThumbnail(song.uri, Size(512, 512), null)
    else openInputStream(ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), song.albumId))
        ?.use { BitmapFactory.decodeStream(it) }
}.getOrNull()

/** Impostazioni + modifiche ai brani, salvate in SharedPreferences ed esposte come stato Compose. */
class Prefs private constructor(ctx: Context) {
    companion object {
        @Volatile private var instance: Prefs? = null
        fun of(ctx: Context) = instance ?: Prefs(ctx.applicationContext).also { instance = it }
    }

    private val sp = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    private val edits = ctx.getSharedPreferences("edits", Context.MODE_PRIVATE)

    var theme by pref("theme", "system")       // system | light | dark
    var dynamic by pref("dynamic", false)      // Material You (Android 12+)
    var accent by pref("accent", "Arancio")
    var sort by pref("sort", "title")          // title | artist | album | date
    var showDuration by pref("showDuration", true)
    var shuffle by pref("shuffle", false)
    var repeat by pref("repeat", false)

    var editsVersion by mutableIntStateOf(0); private set

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
                    else -> error("tipo non supportato: $value")
                }
            }.apply()
        }
    }
}
