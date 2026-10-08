@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.mp3

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Visualizer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player as Exo
import androidx.media3.exoplayer.ExoPlayer
import org.json.JSONArray
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Coda in stile Spotify: [upNext] (brani aggiunti a mano) ha la precedenza, poi si continua con il contesto
 * [queue] (album/lista avviata) dall'ultimo brano di contesto riprodotto. Avviare un nuovo contesto non svuota [upNext].
 * Motore ExoPlayer (Media3): il prossimo brano ([planned]) è già accodato nel player, così il passaggio è senza pausa.
 * Dissolvenza incrociata: un secondo ExoPlayer sulla stessa sessione audio (equalizzatore e visualizzatore valgono per entrambi).
 */
class Player(private val ctx: Context, private val prefs: Prefs) {
    private val handler = Handler(Looper.getMainLooper())
    private val am = ctx.getSystemService(AudioManager::class.java)
    private val audioAttrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()

    val sessionId: Int = androidx.media3.common.util.Util.generateAudioSessionIdV21(ctx)
    private var mp: ExoPlayer = newPlayer()
    private var fadingOut: ExoPlayer? = null
    val effects = Effects(sessionId, prefs)
    private var visualizer: Visualizer? = null

    /** Chiamato a ogni cambio di stato: il servizio aggiorna sessione multimediale, notifica e widget. */
    var onChange: () -> Unit = {}

    var queue: List<Song> by mutableStateOf(emptyList()); private set
    var queueName by mutableStateOf(""); private set
    var upNext: List<Song> by mutableStateOf(emptyList()); private set
    var current: Song? by mutableStateOf(null); private set
    var playing by mutableStateOf(false); private set
    /** False finché non parte la prima riproduzione: dopo "riprendi dove eri" il brano è caricato ma la notifica non compare. */
    var started = false; private set
    var sleepEndsAt: Long? by mutableStateOf(null); private set
    /** Timer "a fine brano": finito questo, il prossimo viene caricato in pausa. */
    var sleepAtEnd by mutableStateOf(false); private set
    /** 32 barre 0..1 dallo spettro audio, quando il visualizzatore è attivo. */
    var spectrum: FloatArray by mutableStateOf(FloatArray(0)); private set
    val position: Int get() = if (current == null) 0 else mp.currentPosition.toInt()

    private var ctxId: Long? = null // ultimo brano di contesto riprodotto (i brani di upNext non lo spostano)
    private val ctxPos get() = queue.indexOfFirst { it.id == ctxId }

    /** Brani di contesto che seguono quello corrente. */
    val nextInContext: List<Song> get() = queue.drop(ctxPos + 1)

    /** Prossimo brano deciso (null a fine coda senza "ripeti") e impostazioni con cui è stato deciso. */
    private var planned: Song? = null
    private var planKey: Triple<Boolean, Boolean, Int>? = null
    private val key get() = Triple(prefs.shuffle, prefs.repeat, prefs.crossfade)
    /** Per la sessione multimediale: precedente nel contesto e prossimo (a fine coda ricomincia, come il tasto "successivo"). */
    val prevSong: Song? get() = if (queue.isEmpty()) null else queue[(ctxPos - 1).mod(queue.size)]
    val nextSong: Song? get() = planned ?: peekNext(auto = false)

    private fun newPlayer(): ExoPlayer {
        val p = ExoPlayer.Builder(ctx).build()
        p.setAudioAttributes(androidx.media3.common.AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), false)
        p.audioSessionId = sessionId
        p.setHandleAudioBecomingNoisy(true) // cuffie scollegate → pausa
        p.setPlaybackSpeed(prefs.speed)
        p.addListener(object : Exo.Listener {
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) { // passaggio senza pausa al brano pre-accodato
                val s = planned ?: return
                if (p !== mp || reason != Exo.MEDIA_ITEM_TRANSITION_REASON_AUTO) return
                p.removeMediaItems(0, p.currentMediaItemIndex)
                advance(s)
                current = s
                planned = null
                plan()
                prefs.addRecent(s.id)
                onChange()
            }
            override fun onPlaybackStateChanged(state: Int) { if (p === mp && state == Exo.STATE_ENDED) next(auto = true) }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (p === mp && !playWhenReady && playing) { playing = false; onChange() }
            }
            override fun onPlayerError(error: PlaybackException) {
                if (p !== mp) return
                if (!started) { stop(); return } // file dell'ultima sessione sparito: niente avviso
                Toast.makeText(ctx, "Impossibile riprodurre: ${current?.title}", Toast.LENGTH_SHORT).show()
                playing = false
                onChange()
            }
        })
        return p
    }

    private fun item(s: Song) = MediaItem.Builder().setUri(s.uri).setMediaId(s.id.toString()).build()

    /** Sceglie il prossimo brano e, senza dissolvenza, lo accoda nel player per il passaggio senza pausa. */
    private fun plan() {
        val k = key
        val cur = current ?: run { planned = null; planKey = k; return }
        // in casuale la scelta già fatta resta valida finché coda e impostazioni non cambiano
        val keep = planned?.takeIf { k == planKey && prefs.shuffle && upNext.isEmpty() && it.id != cur.id && queue.any { q -> q.id == it.id } }
        planKey = k
        val next = keep ?: peekNext(auto = true)
        val queued = next != null && prefs.crossfade == 0 && !sleepAtEnd
        if (next?.id == planned?.id && (mp.mediaItemCount > 1) == queued) return
        planned = next
        if (mp.mediaItemCount > 1) mp.removeMediaItems(1, mp.mediaItemCount)
        if (queued) mp.addMediaItem(item(next!!))
    }

    fun play(list: List<Song>, song: Song, name: String) {
        queue = list
        queueName = name
        ctxId = song.id
        start(song)
    }

    fun enqueue(song: Song) { upNext = upNext + song; plan() }
    fun playNext(song: Song) { upNext = listOf(song) + upNext; plan() }
    fun removeUpNext(i: Int) { upNext = upNext.filterIndexed { j, _ -> j != i }; plan() }
    fun moveUpNext(from: Int, to: Int) { val l = upNext.toMutableList(); l.add(to, l.removeAt(from)); upNext = l; plan() }
    fun clearUpNext() { upNext = emptyList(); plan() }
    fun playUpNext(i: Int) { val s = upNext[i]; upNext = upNext.filterIndexed { j, _ -> j != i }; start(s) }
    fun playInContext(song: Song) { ctxId = song.id; start(song) }

    fun toggle() { if (playing) pause() else resume() }

    fun pause() {
        if (!playing) return
        endFade()
        playing = false
        mp.pause()
        onChange()
    }

    fun resume() {
        if (current == null || playing || !requestFocus()) return
        when (mp.playbackState) { Exo.STATE_IDLE -> mp.prepare(); Exo.STATE_ENDED -> mp.seekTo(0) } // dopo un errore / a fine coda
        started = true
        playing = true
        mp.play()
        schedule()
        onChange()
    }

    /** Prossimo brano secondo coda manuale, contesto, casuale e ripeti; null = fine. */
    private fun peekNext(auto: Boolean): Song? {
        if (upNext.isNotEmpty()) return upNext[0]
        if (queue.isEmpty()) return null
        return when {
            prefs.shuffle && queue.size > 1 -> queue[(queue.indices - ctxPos).random()]
            ctxPos + 1 < queue.size -> queue[ctxPos + 1]
            prefs.repeat || !auto -> queue[0]
            else -> null
        }
    }

    private fun advance(song: Song) {
        if (upNext.isNotEmpty() && upNext[0].id == song.id) upNext = upNext.drop(1) else ctxId = song.id
    }

    fun next(auto: Boolean = false) {
        endFade()
        if (planKey != key) plan()
        if (auto && sleepAtEnd) { // timer a fine brano: prossimo pronto ma in pausa
            sleepAtEnd = false
            val s = planned ?: peekNext(true)
            if (s != null) { advance(s); load(s) } else playing = false
            onChange()
            return
        }
        val s = planned ?: peekNext(auto) ?: run { playing = false; onChange(); return }
        advance(s)
        start(s)
    }

    /** Carica [song] in pausa (ripresa all'avvio, timer a fine brano). */
    private fun load(song: Song, pos: Long = 0) {
        mp.setMediaItem(item(song), pos)
        mp.prepare()
        mp.playWhenReady = false
        current = song
        playing = false
        planned = null
        plan()
    }

    fun prev() {
        if (current == null) return
        endFade()
        val p = prevSong
        if (mp.currentPosition > 3000 || p == null) { mp.seekTo(0); onChange(); return }
        playInContext(p)
    }

    fun seekTo(ms: Int) { if (current != null) { endFade(); mp.seekTo(ms.toLong()); onChange() } }

    fun stop() {
        endFade()
        abandonFocus()
        mp.stop()
        mp.clearMediaItems()
        current = null
        planned = null
        playing = false
        started = false
        onChange()
    }

    fun setSpeed(s: Float) { prefs.speed = s; mp.setPlaybackSpeed(s) }

    /** Timer di spegnimento: mette in pausa dopo [minutes]; null lo annulla. */
    fun setSleep(minutes: Int?) {
        handler.removeCallbacks(sleep)
        sleepEndsAt = minutes?.let { System.currentTimeMillis() + it * 60_000L }
        if (minutes != null) handler.postDelayed(sleep, minutes * 60_000L)
        if (sleepAtEnd) { sleepAtEnd = false; plan() }
    }
    fun stopAtEnd(on: Boolean) { setSleep(null); sleepAtEnd = on; plan() }
    private val sleep = Runnable { pause(); sleepEndsAt = null }

    // Ogni 250 ms mentre suona: ripianifica se cambiano casuale/ripeti/dissolvenza, avvia la dissolvenza quando mancano prefs.crossfade secondi.
    private val tick = object : Runnable {
        override fun run() {
            if (!playing) return
            if (planKey != key) plan()
            maybeCrossfade()
            handler.postDelayed(this, 250)
        }
    }
    private fun schedule() { handler.removeCallbacks(tick); handler.postDelayed(tick, 250) }

    private fun maybeCrossfade() {
        val ms = prefs.crossfade * 1000
        if (ms <= 0 || fadingOut != null || current == null || sleepAtEnd) return
        val dur = mp.duration
        if (dur <= 0 || dur - mp.currentPosition > ms) return
        val song = planned ?: return
        val np = newPlayer()
        np.setMediaItem(item(song))
        np.prepare()
        np.volume = 0f
        np.play()
        val old = mp
        mp = np
        fadingOut = old
        advance(song)
        current = song
        planned = null
        plan()
        prefs.addRecent(song.id)
        onChange()
        val steps = 25
        val stepMs = (ms / steps).toLong()
        var i = 0
        val fade = object : Runnable {
            override fun run() {
                if (fadingOut !== old) return
                i++
                val t = i / steps.toFloat()
                old.volume = 1 - t
                np.volume = t
                if (i < steps) handler.postDelayed(this, stepMs) else endFade()
            }
        }
        handler.postDelayed(fade, stepMs)
    }

    private fun endFade() {
        fadingOut?.let { it.stop(); it.release() }
        fadingOut = null
        mp.volume = 1f
    }

    /** Visualizzatore (serve il permesso RECORD_AUDIO): alimenta [spectrum] a circa 10 fps. */
    fun setVisualizer(on: Boolean) {
        if (!on) { visualizer?.release(); visualizer = null; spectrum = FloatArray(0); return }
        if (visualizer != null) return
        visualizer = runCatching {
            Visualizer(sessionId).apply {
                captureSize = 256
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, w: ByteArray?, rate: Int) {}
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray, rate: Int) {
                        // bin 1..32 (fino a ~5,5 kHz), modulo normalizzato e compresso con la radice per renderlo leggibile
                        spectrum = FloatArray(32) { i ->
                            val re = fft[2 * (i + 1)].toFloat(); val im = fft[2 * (i + 1) + 1].toFloat()
                            sqrt((hypot(re, im) / 128f).coerceIn(0f, 1f))
                        }
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                enabled = true
            }
        }.getOrNull()
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
        plan()
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
        load(song, s.optLong("pos"))
        onChange()
    }

    fun release() {
        setSleep(null)
        endFade()
        setVisualizer(false)
        abandonFocus()
        effects.release()
        mp.release()
    }

    private fun start(song: Song) {
        endFade()
        current = song
        started = true
        playing = requestFocus()
        mp.setMediaItem(item(song))
        mp.prepare()
        mp.playWhenReady = playing
        planned = null
        plan()
        prefs.addRecent(song.id)
        schedule()
        onChange()
    }

    // Audio focus gestito qui e non da ExoPlayer: con due player (dissolvenza) il secondo toglierebbe il focus al primo.
    // Pausa per chiamate/altre app, volume ridotto per notifiche vocali, ripresa quando torna il focus.
    private var resumeOnGain = false
    private val focusListener = AudioManager.OnAudioFocusChangeListener { f ->
        when (f) {
            AudioManager.AUDIOFOCUS_GAIN -> { mp.volume = 1f; if (resumeOnGain) resume(); resumeOnGain = false }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> mp.volume = 0.2f
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
