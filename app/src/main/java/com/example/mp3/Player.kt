package com.example.mp3

import android.content.Context
import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class Player(private val ctx: Context, private val prefs: Prefs) {
    private val mp = MediaPlayer().apply { setOnCompletionListener { next(auto = true) } }

    var queue: List<Song> by mutableStateOf(emptyList()); private set
    var current: Song? by mutableStateOf(null); private set
    var playing by mutableStateOf(false); private set
    val position: Int get() = if (current == null) 0 else mp.currentPosition

    fun play(list: List<Song>, song: Song) { queue = list; start(song) }

    fun toggle() {
        if (current == null) return
        if (mp.isPlaying) mp.pause() else mp.start()
        playing = mp.isPlaying
    }

    fun next(auto: Boolean = false) {
        val i = index()
        if (i < 0) return
        when {
            prefs.shuffle && queue.size > 1 -> start(queue.filterIndexed { j, _ -> j != i }.random())
            i + 1 < queue.size -> start(queue[i + 1])
            prefs.repeat || !auto -> start(queue[0])
            else -> playing = false
        }
    }

    fun prev() {
        val i = index()
        if (i < 0) return
        if (mp.currentPosition > 3000) mp.seekTo(0) else start(queue[(i - 1).mod(queue.size)])
    }

    fun seekTo(ms: Int) { if (current != null) mp.seekTo(ms) }

    /** Dopo una modifica ai metadati, aggiorna coda e brano corrente con le nuove versioni. */
    fun refresh(all: List<Song>) {
        val byId = all.associateBy { it.id }
        queue = queue.map { byId[it.id] ?: it }
        current = current?.let { byId[it.id] ?: it }
    }

    fun release() = mp.release()

    private fun index() = queue.indexOfFirst { it.id == current?.id }

    private fun start(song: Song) {
        current = song
        playing = runCatching { mp.reset(); mp.setDataSource(ctx, song.uri); mp.prepare(); mp.start() }
            .onFailure { Toast.makeText(ctx, "Impossibile riprodurre: ${song.title}", Toast.LENGTH_SHORT).show() }
            .isSuccess
    }
}
