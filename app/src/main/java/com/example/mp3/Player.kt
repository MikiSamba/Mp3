package com.example.mp3

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray

/**
 * Coda in stile Spotify: [upNext] (brani aggiunti a mano) ha la precedenza, poi si continua con il contesto
 * [queue] (album/lista avviata) dall'ultimo brano di contesto riprodotto. Avviare un nuovo contesto non svuota [upNext].
 */
class Player(private val ctx: Context, private val prefs: Prefs) {
    private val audioAttrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val mp = MediaPlayer().apply { setAudioAttributes(audioAttrs); setOnCompletionListener { next(auto = true) } }
    private val handler = Handler(Looper.getMainLooper())
    private val am = ctx.getSystemService(AudioManager::class.java)
    val effects = Effects(mp.audioSessionId, prefs)

    /** Chiamato a ogni cambio di stato: il servizio aggiorna MediaSession e notifica. */
    var onChange: () -> Unit = {}

    var queue: List<Song> by mutableStateOf(emptyList()); private set
    var queueName by mutableStateOf(""); private set
    var upNext: List<Song> by mutableStateOf(emptyList()); private set
    var current: Song? by mutableStateOf(null); private set
    var playing by mutableStateOf(false); private set
    var sleepEndsAt: Long? by mutableStateOf(null); private set
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
    fun playNext(song: Song) { upNext = listOf(song) + upNext }
    fun removeUpNext(i: Int) { upNext = upNext.filterIndexed { j, _ -> j != i } }
    fun clearUpNext() { upNext = emptyList() }
    fun playUpNext(i: Int) { val s = upNext[i]; removeUpNext(i); start(s) }
    fun playInContext(song: Song) { ctxId = song.id; start(song) }

    fun toggle() { if (playing) pause() else resume() }

    fun pause() {
        if (!playing) return
        mp.pause()
        playing = false
        onChange()
    }

    fun resume() {
        if (current == null || playing || !requestFocus()) return
        mp.start()
        applySpeed()
        playing = true
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
        abandonFocus()
        mp.reset()
        current = null
        playing = false
        onChange()
    }

    fun setSpeed(s: Float) { prefs.speed = s; applySpeed() }

    // Impostare playbackParams avvia la riproduzione, quindi solo mentre sta suonando.
    private fun applySpeed() { if (playing) runCatching { mp.playbackParams = mp.playbackParams.setSpeed(prefs.speed) } }

    /** Timer di spegnimento: mette in pausa dopo [minutes]; null lo annulla. */
    fun setSleep(minutes: Int?) {
        handler.removeCallbacks(sleep)
        sleepEndsAt = minutes?.let { System.currentTimeMillis() + it * 60_000L }
        if (minutes != null) handler.postDelayed(sleep, minutes * 60_000L)
    }
    private val sleep = Runnable { pause(); sleepEndsAt = null }

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

    fun saveState() = prefs.saveState(current?.id, position, queue.map { it.id }, upNext.map { it.id }, queueName)

    /** "Riprendi dove eri": ricarica in pausa l'ultima sessione, se non è già in corso nulla. */
    // ponytail: la posizione è quella dell'ultimo cambio di stato (pausa, salto, seek), non del momento esatto in cui l'app è stata chiusa.
    fun restore(all: List<Song>) {
        if (current != null) return
        val s = prefs.loadState() ?: return
        val byId = all.associateBy { it.id }
        val song = byId[s.optLong("current", -1)] ?: return
        queue = ids(s.optJSONArray("queue")).mapNotNull { byId[it] }
        upNext = ids(s.optJSONArray("upNext")).mapNotNull { byId[it] }
        queueName = s.optString("name")
        ctxId = song.id
        if (runCatching { mp.reset(); mp.setDataSource(ctx, song.uri); mp.prepare(); mp.seekTo(s.optInt("pos")) }.isSuccess) current = song
    }

    fun release() {
        setSleep(null)
        abandonFocus()
        effects.release()
        mp.release()
    }

    private fun start(song: Song) {
        current = song
        playing = requestFocus() && runCatching { mp.reset(); mp.setDataSource(ctx, song.uri); mp.prepare(); mp.start() }
            .onFailure { Toast.makeText(ctx, "Impossibile riprodurre: ${song.title}", Toast.LENGTH_SHORT).show() }
            .isSuccess
        applySpeed()
        prefs.addRecent(song.id)
        onChange()
    }

    // Audio focus: pausa per chiamate/altre app, volume ridotto per notifiche vocali, ripresa quando torna il focus.
    private var resumeOnGain = false
    private val focusListener = AudioManager.OnAudioFocusChangeListener { f ->
        when (f) {
            AudioManager.AUDIOFOCUS_GAIN -> { mp.setVolume(1f, 1f); if (resumeOnGain) resume(); resumeOnGain = false }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> mp.setVolume(0.2f, 0.2f)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> { resumeOnGain = playing; pause() }
            else -> { resumeOnGain = false; pause() }
        }
    }
    private val focusRequest = if (Build.VERSION.SDK_INT >= 26)
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(audioAttrs).setOnAudioFocusChangeListener(focusListener).build()
    else null

    @Suppress("DEPRECATION")
    private fun requestFocus() = AudioManager.AUDIOFOCUS_REQUEST_GRANTED ==
        if (Build.VERSION.SDK_INT >= 26) am.requestAudioFocus(focusRequest!!)
        else am.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)

    @Suppress("DEPRECATION")
    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= 26) am.abandonAudioFocusRequest(focusRequest!!) else am.abandonAudioFocus(focusListener)
    }
}

private fun ids(a: JSONArray?) = if (a == null) emptyList() else List(a.length()) { a.getLong(it) }

/** Equalizzatore e bass boost nativi di Android sulla sessione audio del player. */
class Effects(sessionId: Int, private val prefs: Prefs) {
    private val eq = runCatching { Equalizer(0, sessionId) }.getOrNull()
    private val bass = runCatching { BassBoost(0, sessionId) }.getOrNull()
    val available get() = eq != null
    val presets: List<String> = eq?.let { e -> List(e.numberOfPresets.toInt()) { e.getPresetName(it.toShort()) } } ?: emptyList()
    val bands: Int = eq?.numberOfBands?.toInt() ?: 0
    val range: IntRange = eq?.bandLevelRange?.let { it[0]..it[1] } ?: 0..0
    var version by mutableIntStateOf(0); private set   // incrementato a ogni modifica, per far ricomporre la UI

    init { apply() }

    fun bandFreqHz(i: Int): Int = (eq?.getCenterFreq(i.toShort()) ?: 0) / 1000
    fun level(i: Int): Int = eq?.getBandLevel(i.toShort())?.toInt() ?: 0

    fun setEnabled(on: Boolean) { prefs.eqEnabled = on; apply() }
    fun setPreset(i: Int) { prefs.eqPreset = i; prefs.eqBands = ""; apply() }
    fun setBand(i: Int, level: Int) {
        val l = IntArray(bands) { level(it) }
        l[i] = level
        prefs.eqBands = l.joinToString(",")
        prefs.eqPreset = -1
        apply()
    }
    fun setBass(strength: Int) { prefs.bassBoost = strength; apply() }

    private fun apply() {
        val e = eq ?: return
        runCatching {
            e.enabled = prefs.eqEnabled
            if (prefs.eqPreset in presets.indices) e.usePreset(prefs.eqPreset.toShort())
            else prefs.eqBands.split(",").mapNotNull { it.toIntOrNull() }.forEachIndexed { i, l -> if (i < bands) e.setBandLevel(i.toShort(), l.toShort()) }
            bass?.let { b -> b.enabled = prefs.eqEnabled && prefs.bassBoost > 0; if (b.strengthSupported) b.setStrength(prefs.bassBoost.toShort()) }
        }
        version++
    }

    fun release() { eq?.release(); bass?.release() }
}
