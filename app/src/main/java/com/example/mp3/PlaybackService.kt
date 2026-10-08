package com.example.mp3

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Binder
import android.os.Build
import android.os.IBinder

const val EXTRA_NOW_PLAYING = "nowPlaying"

/** Possiede il Player: la musica continua con l'app chiusa; espone MediaSession + notifica MediaStyle. */
class PlaybackService : Service() {
    inner class LocalBinder : Binder() { val player get() = this@PlaybackService.player }

    lateinit var player: Player; private set
    private lateinit var session: MediaSession
    private val nm by lazy { getSystemService(NotificationManager::class.java) }
    private var art: Pair<Long, Bitmap?>? = null
    private var foreground = false
    private val noisy = object : BroadcastReceiver() { // cuffie scollegate → pausa
        override fun onReceive(c: Context?, i: Intent?) { player.pause() }
    }

    override fun onCreate() {
        super.onCreate()
        player = Player(this, Prefs.of(this)).also { it.onChange = ::update }
        session = MediaSession(this, "Musica").apply {
            @Suppress("DEPRECATION")
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { if (!player.playing) player.toggle() }
                override fun onPause() { if (player.playing) player.toggle() }
                override fun onSkipToNext() = player.next()
                override fun onSkipToPrevious() = player.prev()
                override fun onSeekTo(pos: Long) = player.seekTo(pos.toInt())
                override fun onStop() = player.stop()
            })
            isActive = true
        }
        session.setSessionActivity(openApp())
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(noisy, filter, RECEIVER_NOT_EXPORTED) else registerReceiver(noisy, filter)
        Thread { // riprendi dove eri: ricarica in pausa l'ultima sessione
            val all = contentResolver.loadSongs().map(Prefs.of(this)::applyEdits)
            android.os.Handler(mainLooper).post { player.restore(all) }
        }.start()
        if (Build.VERSION.SDK_INT >= 26) {
            nm.deleteNotificationChannel("playback") // canale 1.2 a bassa priorità: alcuni OEM non lo mostrano su lock screen/isola
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Riproduzione", NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            })
        }
    }

    private fun openApp() = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).putExtra(EXTRA_NOW_PLAYING, true),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    override fun onBind(intent: Intent?): IBinder = LocalBinder()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> player.toggle()
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

    private fun update() {
        player.saveState()
        val song = player.current
        if (song == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            nm.cancel(NOTIF_ID)
            foreground = false
            session.setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_STOPPED, 0, 0f).build())
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
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_STOP
                )
                .setState(if (player.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, player.position.toLong(), if (player.playing) 1f else 0f)
                .build()
        )
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

    private companion object {
        const val CHANNEL = "media"
        const val NOTIF_ID = 1
        const val ACTION_TOGGLE = "com.example.mp3.TOGGLE"
        const val ACTION_NEXT = "com.example.mp3.NEXT"
        const val ACTION_PREV = "com.example.mp3.PREV"
        const val ACTION_STOP = "com.example.mp3.STOP"
    }
}
