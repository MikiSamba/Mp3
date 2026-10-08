package com.example.mp3

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.MediaDescription
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.service.media.MediaBrowserService

const val EXTRA_NOW_PLAYING = "nowPlaying"

/**
 * Possiede il Player: la musica continua con l'app chiusa. Espone MediaSession + notifica MediaStyle,
 * aggiorna il widget, ed è un MediaBrowserService: Android Auto, assistente vocale e ripresa dal pannello media
 * sfogliano la libreria da qui.
 */
class PlaybackService : MediaBrowserService() {
    inner class LocalBinder : Binder() { val player get() = this@PlaybackService.player }

    lateinit var player: Player; private set
    private lateinit var session: MediaSession
    private val prefs by lazy { Prefs.of(this) }
    private val nm by lazy { getSystemService(NotificationManager::class.java) }
    private var art: Pair<Long, Bitmap?>? = null
    private var foreground = false
    private val noisy = object : BroadcastReceiver() { // cuffie scollegate → pausa
        override fun onReceive(c: Context?, i: Intent?) { player.pause() }
    }

    override fun onCreate() {
        super.onCreate()
        player = Player(this, prefs).also { it.onChange = ::update }
        session = MediaSession(this, "Musica").apply {
            @Suppress("DEPRECATION")
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { if (player.current == null) { player.restore(library()); if (player.current == null) playAll() }; player.resume() }
                override fun onPause() = player.pause()
                override fun onSkipToNext() = player.next()
                override fun onSkipToPrevious() = player.prev()
                override fun onSeekTo(pos: Long) = player.seekTo(pos.toInt())
                override fun onStop() = player.stop()
                override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) = playMediaId(mediaId)
                override fun onPrepareFromMediaId(mediaId: String, extras: Bundle?) { if (player.current == null) player.restore(library()) }
                override fun onPrepare() { if (player.current == null) player.restore(library()) }
                override fun onPlayFromSearch(query: String?, extras: Bundle?) = playSearch(query.orEmpty())
            })
            setSessionActivity(openApp())
            setPlaybackState(state(PlaybackState.STATE_STOPPED, 0, 0f))
            isActive = true
        }
        sessionToken = session.sessionToken
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(noisy, filter, RECEIVER_NOT_EXPORTED) else registerReceiver(noisy, filter)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.deleteNotificationChannel("playback") // canale 1.2 a bassa priorità: alcuni OEM non lo mostrano su lock screen/isola
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Riproduzione", NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            })
        }
        Thread { // riprendi dove eri: ricarica in pausa l'ultima sessione
            val all = library()
            Handler(mainLooper).post { player.restore(all) }
        }.start()
    }

    override fun onBind(intent: Intent?): IBinder? =
        if (intent?.action == SERVICE_INTERFACE) super.onBind(intent) else LocalBinder()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> if (player.current == null) { player.restore(library()); if (player.current == null) playAll() else player.resume() } else player.toggle()
            ACTION_NEXT -> player.next()
            ACTION_PREV -> player.prev()
            ACTION_STOP -> player.stop()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(noisy)
        player.saveState()
        session.release()
        player.release()
        super.onDestroy()
    }

    // ---- Libreria sfogliabile (Android Auto, assistente, ripresa media) ----

    // ponytail: la libreria viene riletta da MediaStore a ogni richiesta; con migliaia di brani conviene una cache.
    private fun library(): List<Song> = runCatching { contentResolver.loadSongs().map(prefs::applyEdits).sortedBy { it.title.lowercase() } }.getOrDefault(emptyList())

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot {
        val recent = rootHints?.getBoolean(BrowserRoot.EXTRA_RECENT) == true
        return BrowserRoot(if (recent) RECENT else ROOT, null)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowser.MediaItem>>) {
        result.detach()
        Thread {
            val lib = library()
            val items: List<MediaBrowser.MediaItem> = when {
                parentId == ROOT -> listOf(
                    dir("songs", "Brani"), dir("albums", "Album"), dir("artists", "Artisti"),
                    dir("playlists", "Playlist"), dir("fav", "Preferiti"), dir("recentlist", "Ascoltati di recente"),
                )
                parentId == RECENT -> prefs.loadState()?.optLong("current", -1)?.let { id -> lib.firstOrNull { it.id == id } }?.let { listOf(song(it, "songs")) }.orEmpty()
                parentId == "albums" -> lib.groupBy { it.album }.entries.sortedBy { it.key.lowercase() }.map { (name, l) ->
                    dir("album:$name", name, l[0].artist, contentResolver.albumArt(l[0])?.let { Bitmap.createScaledBitmap(it, 160, 160, true) })
                }
                parentId == "artists" -> lib.map { it.artist }.distinct().sortedBy { it.lowercase() }.map { dir("artist:$it", it) }
                parentId == "playlists" -> prefs.playlists.keys.map { dir("pl:$it", it, brani(prefs.playlists[it].orEmpty().size)) }
                else -> songsOf(parentId, lib).map { song(it, parentId) }
            }
            result.sendResult(items.toMutableList())
        }.start()
    }

    private fun songsOf(parent: String, lib: List<Song>): List<Song> = when {
        parent == "songs" -> lib
        parent.startsWith("album:") -> lib.filter { it.album == parent.removePrefix("album:") }.sortedBy { it.track }
        parent.startsWith("artist:") -> lib.filter { it.artist == parent.removePrefix("artist:") }
        parent.startsWith("pl:") -> prefs.playlists[parent.removePrefix("pl:")].orEmpty().mapNotNull { id -> lib.firstOrNull { it.id == id } }
        parent == "fav" -> lib.filter { it.id in prefs.favorites }
        parent == "recentlist" -> prefs.recents.mapNotNull { id -> lib.firstOrNull { it.id == id } }
        else -> lib
    }

    private fun nameOf(parent: String) = when (parent) {
        "songs" -> "Brani"; "fav" -> "Preferiti"; "recentlist" -> "Ascoltati di recente"
        else -> parent.substringAfter(':')
    }

    private fun dir(id: String, title: String, subtitle: String? = null, icon: Bitmap? = null) = MediaBrowser.MediaItem(
        MediaDescription.Builder().setMediaId(id).setTitle(title).setSubtitle(subtitle).setIconBitmap(icon).build(),
        MediaBrowser.MediaItem.FLAG_BROWSABLE,
    )

    private fun song(s: Song, parent: String) = MediaBrowser.MediaItem(
        MediaDescription.Builder().setMediaId("$parent|${s.id}").setTitle(s.title).setSubtitle(s.artist).setDescription(s.album).build(),
        MediaBrowser.MediaItem.FLAG_PLAYABLE,
    )

    private fun brani(n: Int) = if (n == 1) "1 brano" else "$n brani"

    private fun playMediaId(mediaId: String) {
        val parts = mediaId.split("|")
        val id = parts.getOrNull(1)?.toLongOrNull() ?: return
        val lib = library()
        val list = songsOf(parts[0], lib)
        val s = list.firstOrNull { it.id == id } ?: lib.firstOrNull { it.id == id } ?: return
        player.play(list.ifEmpty { listOf(s) }, s, nameOf(parts[0]))
    }

    private fun playSearch(query: String) {
        val lib = library()
        val q = query.trim()
        val hits = if (q.isEmpty()) lib else lib.filter { s -> listOf(s.title, s.artist, s.album).any { it.contains(q, ignoreCase = true) } }
        if (hits.isNotEmpty()) player.play(hits, hits[0], if (q.isEmpty()) "Brani" else "Ricerca")
    }

    private fun playAll() { val lib = library(); if (lib.isNotEmpty()) player.play(lib, lib[0], "Brani") }

    // ---- Notifica, sessione, widget ----

    private fun openApp() = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).putExtra(EXTRA_NOW_PLAYING, true),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun state(st: Int, pos: Long, speed: Float) = PlaybackState.Builder()
        .setActions(
            PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_STOP or
                PlaybackState.ACTION_PLAY_FROM_MEDIA_ID or PlaybackState.ACTION_PREPARE_FROM_MEDIA_ID or
                PlaybackState.ACTION_PLAY_FROM_SEARCH or PlaybackState.ACTION_PREPARE
        )
        .setState(st, pos, speed)
        .build()

    private fun update() {
        player.saveState()
        val song = player.current
        if (song == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            nm.cancel(NOTIF_ID)
            foreground = false
            session.setPlaybackState(state(PlaybackState.STATE_STOPPED, 0, 0f))
            MusicWidget.render(this, null, false, null)
            stopSelf()
            return
        }
        // ponytail: copertina caricata sul main thread (file locale, pochi ms) e cachata per brano.
        val bmp = art?.takeIf { it.first == song.id }?.second ?: contentResolver.albumArt(song).also { art = song.id to it }
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, song.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, song.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, song.album)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, song.duration)
                .apply { if (bmp != null) putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bmp) }
                .build()
        )
        session.setPlaybackState(state(if (player.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, player.position.toLong(), if (player.playing) prefs.speed else 0f))
        MusicWidget.render(this, song, player.playing, bmp)
        val n = notification(song, bmp)
        if (player.playing) {
            // ponytail: su Android 12+ avviare il foreground dallo sfondo (es. tasto cuffie) può essere vietato: fallback a notifica normale.
            runCatching {
                val self = Intent(this, PlaybackService::class.java)
                if (!foreground) { if (Build.VERSION.SDK_INT >= 26) startForegroundService(self) else startService(self) }
                startForeground(NOTIF_ID, n)
                foreground = true
            }.onFailure { nm.notify(NOTIF_ID, n) }
        } else {
            stopForeground(STOP_FOREGROUND_DETACH)
            foreground = false
            nm.notify(NOTIF_ID, n)
        }
    }

    private fun notification(song: Song, art: Bitmap?): Notification {
        fun pending(action: String) = PendingIntent.getService(
            this, 0, Intent(this, PlaybackService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE,
        )
        fun action(icon: Int, title: String, act: String) =
            Notification.Action.Builder(Icon.createWithResource(this, icon), title, pending(act)).build()
        @Suppress("DEPRECATION")
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        return b.setSmallIcon(R.drawable.ic_note)
            .setContentTitle(song.title).setContentText(song.artist).setSubText(song.album)
            .setLargeIcon(art)
            .setContentIntent(openApp())
            .setDeleteIntent(pending(ACTION_STOP))
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(player.playing)
            .apply { if (art != null) { setColor(art.averageColor()); setColorized(true) } }
            .addAction(action(android.R.drawable.ic_media_previous, "Precedente", ACTION_PREV))
            .addAction(
                if (player.playing) action(android.R.drawable.ic_media_pause, "Pausa", ACTION_TOGGLE)
                else action(android.R.drawable.ic_media_play, "Riproduci", ACTION_TOGGLE)
            )
            .addAction(action(android.R.drawable.ic_media_next, "Successivo", ACTION_NEXT))
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    companion object {
        const val ACTION_TOGGLE = "com.example.mp3.TOGGLE"
        const val ACTION_NEXT = "com.example.mp3.NEXT"
        const val ACTION_PREV = "com.example.mp3.PREV"
        const val ACTION_STOP = "com.example.mp3.STOP"
        private const val CHANNEL = "media"
        private const val NOTIF_ID = 1
        private const val ROOT = "root"
        private const val RECENT = "recent"
    }
}
