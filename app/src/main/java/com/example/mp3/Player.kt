package com.example.mp3

import android.content.Context
import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Coda in stile Spotify: [upNext] (brani aggiunti a mano) ha la precedenza, poi si continua con il contesto
 * [queue] (album/lista avviata) dall'ultimo brano di contesto riprodotto. Avviare un nuovo contesto non svuota [upNext].
 */
class Player(private val ctx: Context, private val prefs: Prefs) {
    private val mp = MediaPlayer().apply { setOnCompletionListener { next(auto = true) } }

    /** Chiamato a ogni cambio di stato: il servizio aggiorna MediaSession e notifica. */
    var onChange: () -> Unit = {}

    var queue: List<Song> by mutableStateOf(emptyList()); private set
    var queueName by mutableStateOf(""); private set
    var upNext: List<Song> by mutableStateOf(emptyList()); private set
    var current: Song? by mutableStateOf(null); private set
    var playing by mutableStateOf(false); private set
    val position: Int get() = if (current == null) 0 else mp.currentPosition

    private var ctxId: Long? = null // ultimo brano di contesto riprodotto (i brani di upNext non lo spostano)
    private val ctxPos get() = queue.indexOfFirst { it.id == ctxId }

    /** Brani di contesto che seguono quello corrente. */
    val nextInContext: List<Song> get() = queue.drop(ctxPos + 1)

    fun play(list: List<Song>, song: Song, name: String) {
        queue = list
        queueName = name
        ctxId = song.id
        start(song)
    }

    fun enqueue(song: Song) { upNext = upNext + song }
    fun removeUpNext(i: Int) { upNext = upNext.filterIndexed { j, _ -> j != i } }
    fun clearUpNext() { upNext = emptyList() }
    fun playUpNext(i: Int) { val s = upNext[i]; removeUpNext(i); start(s) }
    fun playInContext(song: Song) { ctxId = song.id; start(song) }

    fun toggle() {
        if (current == null) return
        if (mp.isPlaying) mp.pause() else mp.start()
        playing = mp.isPlaying
        onChange()
    }

    fun next(auto: Boolean = false) {
        if (upNext.isNotEmpty()) { playUpNext(0); return }
        val i = when {
            queue.isEmpty() -> -1
            prefs.shuffle && queue.size > 1 -> (queue.indices - ctxPos).random()
            ctxPos + 1 < queue.size -> ctxPos + 1
            prefs.repeat || !auto -> 0
            else -> -1
        }
        if (i < 0) { playing = false; onChange(); return }
        playInContext(queue[i])
    }

    fun prev() {
        if (current == null) return
        if (mp.currentPosition > 3000 || queue.isEmpty()) { mp.seekTo(0); onChange(); return }
        playInContext(queue[(ctxPos - 1).mod(queue.size)])
    }

    fun seekTo(ms: Int) { if (current != null) { mp.seekTo(ms); onChange() } }

    fun stop() {
        mp.reset()
        current = null
        playing = false
        onChange()
    }

    /** Dopo una modifica ai metadati, aggiorna code e brano corrente con le nuove versioni. */
    fun refresh(all: List<Song>) {
        val byId = all.associateBy { it.id }
        queue = queue.map { byId[it.id] ?: it }
        upNext = upNext.map { byId[it.id] ?: it }
        current = current?.let { byId[it.id] ?: it }
    }

    /** Brano eliminato dal dispositivo: via dalle code; se era in riproduzione passa al successivo. */
    fun remove(id: Long) {
        if (current?.id == id) { next(); if (current?.id == id) stop() }
        upNext = upNext.filter { it.id != id }
        queue = queue.filter { it.id != id }
    }

    fun release() = mp.release()

    private fun start(song: Song) {
        current = song
        playing = runCatching { mp.reset(); mp.setDataSource(ctx, song.uri); mp.prepare(); mp.start() }
            .onFailure { Toast.makeText(ctx, "Impossibile riprodurre: ${song.title}", Toast.LENGTH_SHORT).show() }
            .isSuccess
        onChange()
    }
}
