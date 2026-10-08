@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.mp3

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player as M3Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.BitmapLoader
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.io.ByteArrayOutputStream

const val EXTRA_NOW_PLAYING = "nowPlaying"

/**
 * Possiede il Player: la musica continua con l'app chiusa. È un MediaLibraryService (Media3): notifica, lock screen,
 * tasti cuffie/Bluetooth, Android Auto, assistente vocale e ripresa dal pannello media passano da qui.
 * Il motore resta il nostro [Player]; [SessionPlayer] lo traduce nel modello di Media3.
 */
class PlaybackService : MediaLibraryService() {
    inner class LocalBinder : Binder() { val player get() = this@PlaybackService.player }

    lateinit var player: Player; private set
    private lateinit var session: MediaLibrarySession
    private lateinit var adapter: SessionPlayer
    private val prefs by lazy { Prefs.of(this) }
    private val main = Handler(Looper.getMainLooper())
    private var art: Pair<Long, Bitmap?>? = null
    private var buttonsKey: Pair<Boolean, Boolean>? = null

    override fun onCreate() {
        super.onCreate()
        player = Player(this, prefs).also { it.onChange = ::update }
        adapter = SessionPlayer()
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.channel_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).setChannelId(CHANNEL).setChannelName(R.string.channel_name).build()
                .apply { setSmallIcon(R.drawable.ic_note) }
        )
        session = MediaLibrarySession.Builder(this, adapter, Callback())
            .setSessionActivity(PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java).putExtra(EXTRA_NOW_PLAYING, true),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ))
            .setBitmapLoader(ArtLoader())
            .build()
        addSession(session) // creata qui e non in onGetSession: senza questa riga Media3 non gestisce notifica e foreground
        Thread { // riprendi dove eri: ricarica in pausa l'ultima sessione
            val all = library()
            main.post { player.restore(all) }
        }.start()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = session

    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent) ?: LocalBinder()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> if (player.current == null) { player.restore(library()); if (player.current == null) playAll() else player.resume() } else player.toggle()
            ACTION_NEXT -> player.next()
            ACTION_PREV -> player.prev()
            ACTION_STOP -> player.stop()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        player.saveState()
        session.release()
        player.release()
        super.onDestroy()
    }

    /** A ogni cambio di stato del Player: stato salvato, sessione Media3 (→ notifica, lock screen, Auto) e widget. */
    private fun update() {
        if (!::session.isInitialized) return
        player.saveState()
        main.post(adapter::refresh) // non dentro a un comando Media3 in corso
        val k = prefs.shuffle to prefs.repeat
        if (k != buttonsKey) { buttonsKey = k; session.setMediaButtonPreferences(buttons()) }
        val song = player.current
        // ponytail: copertina caricata sul main thread (file locale, pochi ms) e cachata per brano.
        val bmp = song?.let { s -> art?.takeIf { it.first == s.id }?.second ?: contentResolver.albumArt(s).also { art = s.id to it } }
        MusicWidget.render(this, song, player.playing, bmp)
    }

    private fun buttons() = listOf(
        CommandButton.Builder(if (prefs.shuffle) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF)
            .setDisplayName("Casuale").setSessionCommand(SessionCommand(CMD_SHUFFLE, Bundle.EMPTY)).build(),
        CommandButton.Builder(if (prefs.repeat) CommandButton.ICON_REPEAT_ALL else CommandButton.ICON_REPEAT_OFF)
            .setDisplayName("Ripeti").setSessionCommand(SessionCommand(CMD_REPEAT, Bundle.EMPTY)).build(),
    )

    /**
     * Traduce lo stato del nostro Player nel modello di Media3. Playlist di al più 3 voci (precedente, corrente, prossimo):
     * basta per i tasti avanti/indietro e la coda di Android Auto senza ricostruire liste di migliaia di brani a ogni cambio.
     */
    private inner class SessionPlayer : SimpleBasePlayer(Looper.getMainLooper()) {
        private val done: ListenableFuture<*> get() = Futures.immediateVoidFuture()
        fun refresh() = invalidateState()

        override fun getState(): State {
            val cur = player.current
            val b = State.Builder().setAvailableCommands(COMMANDS).setAudioAttributes(AUDIO_ATTRS)
                .setShuffleModeEnabled(prefs.shuffle)
                .setRepeatMode(if (prefs.repeat) M3Player.REPEAT_MODE_ALL else M3Player.REPEAT_MODE_OFF)
                .setPlaybackParameters(PlaybackParameters(prefs.speed))
            if (cur == null) return b.build()
            val prev = player.prevSong?.takeIf { it.id != cur.id }
            val next = player.nextSong?.takeIf { it.id != cur.id }
            return b.setPlaylist(listOfNotNull(prev?.let { data("p", it) }, data("c", cur), next?.let { data("n", it) }))
                .setCurrentMediaItemIndex(if (prev != null) 1 else 0)
                .setPlaybackState(if (player.started) M3Player.STATE_READY else M3Player.STATE_IDLE)
                .setPlayWhenReady(player.playing, M3Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .setContentPositionMs { player.position.toLong() }
                .build()
        }

        private fun data(prefix: String, s: Song) = MediaItemData.Builder("$prefix${s.id}")
            .setMediaItem(mediaItem(s, "queue", art = true)).setDurationUs(s.duration * 1000).setIsSeekable(true).build()

        override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
            if (!playWhenReady) player.pause()
            else {
                if (player.current == null) { player.restore(library()); if (player.current == null) playAll() }
                player.resume()
            }
            return done
        }
        override fun handlePrepare(): ListenableFuture<*> { if (player.current == null) player.restore(library()); return done }
        override fun handleStop(): ListenableFuture<*> { player.stop(); return done }
        override fun handleRelease(): ListenableFuture<*> = done
        override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> { prefs.shuffle = shuffleModeEnabled; update(); return done }
        override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> { prefs.repeat = repeatMode != M3Player.REPEAT_MODE_OFF; update(); return done }
        override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> { player.setSpeed(playbackParameters.speed); return done }

        override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
            when {
                seekCommand == M3Player.COMMAND_SEEK_TO_NEXT || seekCommand == M3Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> player.next()
                seekCommand == M3Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> player.prev()
                seekCommand == M3Player.COMMAND_SEEK_TO_PREVIOUS && mediaItemIndex != currentMediaItemIndex -> player.prev()
                mediaItemIndex > currentMediaItemIndex -> player.next()
                mediaItemIndex < currentMediaItemIndex -> player.prev()
                else -> player.seekTo(if (positionMs == C.TIME_UNSET) 0 else positionMs.toInt())
            }
            return done
        }

        /** Da Android Auto, assistente o ripresa: l'id è "lista|idBrano"; una ricerca vocale arriva come searchQuery. */
        override fun handleSetMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<*> {
            val it = mediaItems.getOrNull(startIndex) ?: mediaItems.firstOrNull() ?: return done
            val query = it.requestMetadata.searchQuery
            val id = it.mediaId.substringAfterLast('|').toLongOrNull()
            when {
                query != null -> playSearch(query)
                id != null && id == player.current?.id -> if (startPositionMs > 0) player.seekTo(startPositionMs.toInt())
                else -> playMediaId(it.mediaId)
            }
            return done
        }
    }

    /** Copertine per notifica e sessione: "musica://art/idBrano" → bitmap dalla cache condivisa con la UI. */
    private inner class ArtLoader : BitmapLoader {
        override fun supportsMimeType(mimeType: String) = mimeType.startsWith("image/")
        override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
            BitmapFactory.decodeByteArray(data, 0, data.size)?.let { Futures.immediateFuture(it) }
                ?: Futures.immediateFailedFuture(IllegalArgumentException("immagine non valida"))
        override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
            val id = uri.lastPathSegment?.toLongOrNull()
            val song = (listOfNotNull(player.current) + player.upNext + player.queue).firstOrNull { it.id == id }
            val bmp = song?.let { contentResolver.albumArt(it) }
            return if (bmp != null) Futures.immediateFuture(bmp) else Futures.immediateFailedFuture(Exception("senza copertina"))
        }
    }

    private inner class Callback : MediaLibrarySession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
            MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                        .add(SessionCommand(CMD_SHUFFLE, Bundle.EMPTY)).add(SessionCommand(CMD_REPEAT, Bundle.EMPTY)).build()
                )
                .build()

        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CMD_SHUFFLE -> prefs.shuffle = !prefs.shuffle
                CMD_REPEAT -> prefs.repeat = !prefs.repeat
            }
            update()
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        // Gli elementi arrivano con il solo mediaId: vengono risolti in handleSetMediaItems.
        override fun onAddMediaItems(session: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: List<MediaItem>): ListenableFuture<List<MediaItem>> =
            Futures.immediateFuture(mediaItems)

        /** Tasto play da cuffie/pannello media ad app chiusa: ultima sessione, altrimenti tutta la libreria. */
        override fun onPlaybackResumption(session: MediaSession, controller: MediaSession.ControllerInfo, isForPlayback: Boolean): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            if (player.current == null) player.restore(library())
            val s = player.current ?: library().firstOrNull()
            return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(listOfNotNull(s?.let { mediaItem(it, "songs", art = true) }), 0, player.position.toLong()))
        }

        override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(dir(if (params?.isRecent == true) RECENT else ROOT, "Musica"), params))

        override fun onGetChildren(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String, page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            bg { LibraryResult.ofItemList(children(parentId), params) }

        override fun onGetItem(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String): ListenableFuture<LibraryResult<MediaItem>> = bg {
            val id = mediaId.substringAfterLast('|').toLongOrNull()
            library().firstOrNull { it.id == id }?.let { LibraryResult.ofItem(mediaItem(it, mediaId.substringBefore('|')), null) }
                ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        }

        override fun onSearch(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String, params: LibraryParams?): ListenableFuture<LibraryResult<Void>> {
            session.notifySearchResultChanged(browser, query, search(query).size, params)
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetSearchResult(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String, page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            bg { LibraryResult.ofItemList(search(query).map { mediaItem(it, "search") }, params) }
    }

    // ---- Libreria sfogliabile (Android Auto, assistente, ripresa media) ----

    // ponytail: la libreria viene riletta da MediaStore a ogni richiesta; con migliaia di brani conviene una cache.
    private fun library(): List<Song> = runCatching { contentResolver.loadSongs().map(prefs::applyEdits).sortedBy { it.title.lowercase() } }.getOrDefault(emptyList())

    private fun <T> bg(block: () -> T): ListenableFuture<T> = SettableFuture.create<T>().also { f ->
        Thread { runCatching(block).onSuccess { f.set(it) }.onFailure { f.setException(it) } }.start()
    }

    private fun children(parentId: String): List<MediaItem> {
        val lib = library()
        return when {
            parentId == ROOT -> listOf(
                dir("songs", "Brani"), dir("albums", "Album"), dir("artists", "Artisti"),
                dir("playlists", "Playlist"), dir("fav", "Preferiti"), dir("recentlist", "Ascoltati di recente"),
            )
            parentId == RECENT -> prefs.loadState()?.optLong("current", -1)?.let { id -> lib.firstOrNull { it.id == id } }?.let { listOf(mediaItem(it, "songs", art = true)) }.orEmpty()
            parentId == "albums" -> lib.groupBy { it.album }.entries.sortedBy { it.key.lowercase() }.map { (name, l) ->
                dir("album:$name", name, l[0].artist, contentResolver.albumArt(l[0]))
            }
            parentId == "artists" -> lib.map { it.artist }.distinct().sortedBy { it.lowercase() }.map { dir("artist:$it", it) }
            parentId == "playlists" -> prefs.playlists.keys.map { dir("pl:$it", it, brani(prefs.playlists[it].orEmpty().size)) }
            else -> songsOf(parentId, lib).map { mediaItem(it, parentId) }
        }
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
        "songs" -> "Brani"; "fav" -> "Preferiti"; "recentlist" -> "Ascoltati di recente"; "search" -> "Ricerca"
        else -> parent.substringAfter(':')
    }

    private fun dir(id: String, title: String, subtitle: String? = null, art: Bitmap? = null): MediaItem = MediaItem.Builder().setMediaId(id).setMediaMetadata(
        MediaMetadata.Builder().setTitle(title).setSubtitle(subtitle).setIsBrowsable(true).setIsPlayable(false).setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            .apply {
                if (art != null) setArtworkData(
                    ByteArrayOutputStream().also { Bitmap.createScaledBitmap(art, 160, 160, true).compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray(),
                    MediaMetadata.PICTURE_TYPE_FRONT_COVER,
                )
            }
            .build()
    ).build()

    /** [art]: solo per i brani in sessione (notifica/lock screen); nelle liste di Auto l'URI sarebbe caricato dal client e fallirebbe. */
    private fun mediaItem(s: Song, parent: String, art: Boolean = false): MediaItem = MediaItem.Builder().setMediaId("$parent|${s.id}").setUri(s.uri).setMediaMetadata(
        MediaMetadata.Builder().setTitle(s.title).setArtist(s.artist).setAlbumTitle(s.album).setSubtitle(s.artist).setDurationMs(s.duration)
            .setIsBrowsable(false).setIsPlayable(true).setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .apply { if (art) setArtworkUri(Uri.parse("musica://art/${s.id}")) } // schema proprio: con content:// il sistema logga un errore a ogni brano
            .build()
    ).build()

    private fun brani(n: Int) = if (n == 1) "1 brano" else "$n brani"

    private fun search(query: String): List<Song> {
        val lib = library()
        val q = query.trim()
        return if (q.isEmpty()) lib else lib.filter { s -> listOf(s.title, s.artist, s.album).any { it.contains(q, ignoreCase = true) } }
    }

    private fun playMediaId(mediaId: String) {
        val id = mediaId.substringAfterLast('|').toLongOrNull() ?: return
        val parent = mediaId.substringBefore('|', "songs")
        val lib = library()
        val list = songsOf(parent, lib)
        val s = list.firstOrNull { it.id == id } ?: lib.firstOrNull { it.id == id } ?: return
        player.play(list.ifEmpty { listOf(s) }, s, nameOf(parent))
    }

    private fun playSearch(query: String) {
        val hits = search(query)
        if (hits.isNotEmpty()) player.play(hits, hits[0], if (query.isBlank()) "Brani" else "Ricerca")
    }

    private fun playAll() { val lib = library(); if (lib.isNotEmpty()) player.play(lib, lib[0], "Brani") }

    companion object {
        const val ACTION_TOGGLE = "com.example.mp3.TOGGLE"
        const val ACTION_NEXT = "com.example.mp3.NEXT"
        const val ACTION_PREV = "com.example.mp3.PREV"
        const val ACTION_STOP = "com.example.mp3.STOP"
        private const val CHANNEL = "media"
        private const val ROOT = "root"
        private const val RECENT = "recent"
        private const val CMD_SHUFFLE = "shuffle"
        private const val CMD_REPEAT = "repeat"
        private val AUDIO_ATTRS = AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build()
        private val COMMANDS = M3Player.Commands.Builder().addAll(
            M3Player.COMMAND_PLAY_PAUSE, M3Player.COMMAND_PREPARE, M3Player.COMMAND_STOP,
            M3Player.COMMAND_SEEK_TO_DEFAULT_POSITION, M3Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, M3Player.COMMAND_SEEK_TO_MEDIA_ITEM,
            M3Player.COMMAND_SEEK_TO_PREVIOUS, M3Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            M3Player.COMMAND_SEEK_TO_NEXT, M3Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            M3Player.COMMAND_SEEK_BACK, M3Player.COMMAND_SEEK_FORWARD, M3Player.COMMAND_SET_SPEED_AND_PITCH,
            M3Player.COMMAND_SET_SHUFFLE_MODE, M3Player.COMMAND_SET_REPEAT_MODE, M3Player.COMMAND_SET_MEDIA_ITEM,
            M3Player.COMMAND_GET_CURRENT_MEDIA_ITEM, M3Player.COMMAND_GET_TIMELINE, M3Player.COMMAND_GET_METADATA, M3Player.COMMAND_GET_AUDIO_ATTRIBUTES,
        ).build()
    }
}
