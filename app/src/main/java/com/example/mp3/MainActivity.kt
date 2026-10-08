@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.mp3

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.app.RecoverableSecurityException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val ACCENTS = linkedMapOf(
    "Arancio" to Color(0xFFFB8C00), "Rosso" to Color(0xFFE53935), "Blu" to Color(0xFF1E88E5),
    "Verde" to Color(0xFF43A047), "Viola" to Color(0xFF8E24AA), "Rosa" to Color(0xFFD81B60),
)
private val TABS = listOf("Brani", "Album", "Artisti", "Raccolta", "Opzioni")
private const val FAV = "fav"
private const val RECENT = "recent"
private const val PL = "pl:"
private val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
private val SLEEP = listOf(15, 30, 45, 60, 90)

class MainActivity : ComponentActivity() {
    private var player by mutableStateOf<Player?>(null)
    private var nowPlayingRequest by mutableIntStateOf(0)
    private var shortcut by mutableStateOf<Pair<String, Int>?>(null) // scorciatoia dall'icona + contatore per rilanciarla
    private var shortcutN = 0
    private fun handle(intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_NOW_PLAYING, false)) nowPlayingRequest++
        intent.getStringExtra("shortcut")?.let { shortcut = it to ++shortcutN }
    }
    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) { player = (binder as PlaybackService.LocalBinder).player }
        override fun onServiceDisconnected(name: ComponentName) { player = null }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        val prefs = Prefs.of(this)
        setContent {
            Themed(prefs) { Surface(Modifier.fillMaxSize()) { player?.let { App(prefs, it, nowPlayingRequest, shortcut) } } }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onStart() {
        super.onStart()
        bindService(Intent(this, PlaybackService::class.java), conn, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        unbindService(conn)
        player = null
        super.onStop()
    }
}

@Composable
private fun Themed(prefs: Prefs, content: @Composable () -> Unit) {
    val dark = when (prefs.theme) { "light" -> false; "dark", "amoled" -> true; else -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    // Icone delle barre di sistema coerenti col tema scelto nell'app, non solo con quello di sistema.
    LaunchedEffect(dark) {
        val t = android.graphics.Color.TRANSPARENT
        val style = if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t)
        (ctx as? ComponentActivity)?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
    val accent = ACCENTS[prefs.accent] ?: ACCENTS.values.first()
    val scheme = when {
        prefs.dynamic && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(primary = accent, onPrimary = Color.White, secondary = accent, primaryContainer = accent.copy(alpha = 0.3f), secondaryContainer = accent.copy(alpha = 0.2f))
        else -> lightColorScheme(primary = accent, secondary = accent, primaryContainer = accent.copy(alpha = 0.15f), secondaryContainer = accent.copy(alpha = 0.12f))
    }
    // Tema "Nero": superfici nere pure, che sugli schermi OLED restano spente.
    val black = if (prefs.theme == "amoled") scheme.copy(
        background = Color.Black, surface = Color.Black, surfaceContainerLowest = Color.Black, surfaceContainerLow = Color.Black,
        surfaceContainer = Color(0xFF0E0E0E), surfaceContainerHigh = Color(0xFF181818), surfaceContainerHighest = Color(0xFF222222), surfaceVariant = Color(0xFF222222),
    ) else scheme
    MaterialTheme(colorScheme = black, content = content)
}

@Composable
private fun App(prefs: Prefs, player: Player, nowPlayingRequest: Int, shortcut: Pair<String, Int>?) {
    val ctx = LocalContext.current
    val perms = if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
                else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    var granted by remember { mutableStateOf(ctx.checkSelfPermission(perms[0]) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted = it[perms[0]] == true }
    LaunchedEffect(Unit) { if (perms.any { ctx.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) ask.launch(perms) }

    var raw by remember { mutableStateOf(emptyList<Song>()) }
    var reloads by remember { mutableIntStateOf(0) }
    LaunchedEffect(granted, reloads) { if (granted) { if (reloads > 0) delay(500); raw = withContext(Dispatchers.IO) { ctx.contentResolver.loadSongs() } } }
    // Libreria aggiornata da sola quando MediaStore cambia (file copiati o cancellati, tag riscritti).
    DisposableEffect(granted) {
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) { override fun onChange(selfChange: Boolean) { reloads++ } }
        if (granted) ctx.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, obs)
        onDispose { ctx.contentResolver.unregisterContentObserver(obs) }
    }
    val songs = remember(raw, prefs.editsVersion, prefs.sort) {
        raw.map(prefs::applyEdits).sortedWith(
            when (prefs.sort) {
                "artist" -> compareBy<Song>({ it.artist.lowercase() }, { it.album.lowercase() }, { it.track })
                "album" -> compareBy<Song>({ it.album.lowercase() }, { it.track })
                "date" -> compareByDescending<Song> { it.dateAdded }
                else -> compareBy<Song> { it.title.lowercase() }
            }
        )
    }
    val byId = remember(songs) { songs.associateBy { it.id } }
    LaunchedEffect(songs) { player.refresh(songs) }

    var tab by remember { mutableIntStateOf(0) }
    var group by remember { mutableStateOf<String?>(null) }   // album, artista, FAV, RECENT o PL+nome aperto
    var query by remember { mutableStateOf<String?>(null) }   // null = ricerca chiusa
    var editing by remember { mutableStateOf<Song?>(null) }
    var info by remember { mutableStateOf<Song?>(null) }
    var deleting by remember { mutableStateOf<Song?>(null) }   // in attesa di conferma (sistema o nostra)
    var toPlaylist by remember { mutableStateOf<Song?>(null) }
    var nowPlaying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Scorciatoie dall'icona: eseguite appena la libreria è caricata, una volta sola.
    var shortcutDone by remember { mutableStateOf<Pair<String, Int>?>(null) }
    LaunchedEffect(shortcut, songs.isEmpty()) {
        val s = shortcut ?: return@LaunchedEffect
        if (songs.isEmpty() || s == shortcutDone) return@LaunchedEffect
        shortcutDone = s
        when (s.first) {
            "shuffle" -> { prefs.shuffle = true; player.play(songs, songs.random(), "Brani"); nowPlaying = true }
            "fav" -> { tab = 3; group = FAV }
        }
    }

    // Avviso aggiornamenti: confronta la build installata con quella pubblicata su GitHub.
    var update by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        val local = localBuild(ctx)
        val remote = withContext(Dispatchers.IO) { fetchLatestBuild() }.build
        if (remote != null && remote > local && remote != prefs.skipBuild) update = remote
    }

    // Scrittura dei tag nel file MP3: su Android 11+ passa dalla conferma di sistema (createWriteRequest).
    var tagWrite by remember { mutableStateOf<TagWrite?>(null) }
    var writeFn: (TagWrite) -> Unit = {}
    val writeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val w = tagWrite; tagWrite = null
        if (r.resultCode == Activity.RESULT_OK && w != null) writeFn(w)
    }
    fun doWrite(w: TagWrite) {
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = ctx.contentResolver.openInputStream(w.song.uri)!!.use { it.readBytes() }
                    // Campi vuoti = lasciati come sono nel file (Android non legge l'anno in TDRC: un campo vuoto non deve cancellarlo).
                    val text = mapOf("TIT2" to w.title, "TPE1" to w.artist, "TALB" to w.album, "TCON" to w.genre, "TDRC" to w.year, "TRCK" to w.track).filterValues { it.isNotBlank() }
                    val edit = TagEdit(text, w.lyrics, w.cover)
                    val out = rewriteId3(bytes, edit) ?: return@runCatching "Tag non gestito (serve ID3v2.3 o 2.4)"
                    ctx.contentResolver.openOutputStream(w.song.uri, "wt")!!.use { it.write(out) }
                    null
                }
            }
            val err = r.exceptionOrNull()
            when {
                err is RecoverableSecurityException && Build.VERSION.SDK_INT >= 29 -> { tagWrite = w; writeLauncher.launch(IntentSenderRequest.Builder(err.userAction.actionIntent.intentSender).build()); return@launch }
                err != null -> Toast.makeText(ctx, "Scrittura non riuscita: ${err.message}", Toast.LENGTH_LONG).show()
                r.getOrNull() != null -> Toast.makeText(ctx, r.getOrNull(), Toast.LENGTH_LONG).show()
                else -> {
                    prefs.saveEdit(w.song.id, null, null, null)
                    if (w.cover != null) invalidateArt(w.song.albumId)
                    Toast.makeText(ctx, "Tag scritti nel file", Toast.LENGTH_SHORT).show()
                    delay(1500); reloads++ // MediaStore rianalizza il file alla chiusura
                }
            }
            tagWrite = null
        }
    }
    writeFn = ::doWrite
    fun requestWrite(w: TagWrite) {
        if (Build.VERSION.SDK_INT >= 30) {
            tagWrite = w
            writeLauncher.launch(IntentSenderRequest.Builder(MediaStore.createWriteRequest(ctx.contentResolver, listOf(w.song.uri)).intentSender).build())
        } else doWrite(w)
    }
    LaunchedEffect(nowPlayingRequest) { if (nowPlayingRequest > 0) nowPlaying = true }
    LaunchedEffect(player.current == null) { if (player.current == null) nowPlaying = false }
    BackHandler(!nowPlaying && (group != null || query != null)) { group = null; query = null }

    fun deleted(s: Song) { player.remove(s.id); raw = raw.filter { it.id != s.id } }
    val systemDelete = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        deleting?.let { if (r.resultCode == Activity.RESULT_OK) deleted(it) }
        deleting = null
    }
    // ponytail: su Android 11+ conferma di sistema; su 10 RecoverableSecurityException; sotto serve WRITE_EXTERNAL_STORAGE, non richiesto.
    fun requestDelete(s: Song) {
        deleting = s
        if (Build.VERSION.SDK_INT >= 30) {
            systemDelete.launch(IntentSenderRequest.Builder(MediaStore.createDeleteRequest(ctx.contentResolver, listOf(s.uri)).intentSender).build())
        }
    }
    fun confirmedDelete(s: Song) {
        try { ctx.contentResolver.delete(s.uri, null, null); deleted(s); deleting = null }
        catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= 29 && e is RecoverableSecurityException) systemDelete.launch(IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build())
            else { Toast.makeText(ctx, "Impossibile eliminare su questa versione di Android", Toast.LENGTH_SHORT).show(); deleting = null }
        }
    }

    val q = query.orEmpty().trim()
    // Cerca in titolo, artista, album, genere e nei testi già scaricati o letti dal file.
    fun matches(s: Song) = q.isEmpty() || listOf(s.title, s.artist, s.album, s.genre).any { it.contains(q, ignoreCase = true) } ||
        prefs.lyrics(s.id)?.contains(q, ignoreCase = true) == true
    val shown = remember(songs, q) { songs.filter(::matches) }
    val g = group
    val groupTitle = when {
        g == FAV -> "Preferiti"
        g == RECENT -> "Ascoltati di recente"
        g != null && g.startsWith(PL) -> g.removePrefix(PL)
        else -> g
    }
    val groupSongs: List<Song> = when {
        g == null -> emptyList()
        g == FAV -> shown.filter { it.id in prefs.favorites }
        g == RECENT -> prefs.recents.mapNotNull { byId[it] }.filter(::matches)
        g.startsWith(PL) -> prefs.playlists[g.removePrefix(PL)].orEmpty().mapNotNull { byId[it] }.filter(::matches)
        tab == 1 -> shown.filter { it.album == g }.sortedBy { it.track }
        else -> shown.filter { it.artist == g }
    }
    val inPlaylist = g?.takeIf { it.startsWith(PL) }?.removePrefix(PL)
    val current = player.current

    Box {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        val qq = query
                        if (qq != null) TextField(qq, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Cerca…") }, singleLine = true)
                        else Text(groupTitle ?: if (tab == 0) "Musica" else TABS[tab], fontWeight = FontWeight.Bold)
                    },
                    navigationIcon = { if (group != null) IconButton({ group = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
                    actions = {
                        if (tab < 4 && !(tab == 3 && group == null)) IconButton({ query = if (query == null) "" else null }) {
                            Icon(if (query == null) Icons.Default.Search else Icons.Default.Close, "Cerca")
                        }
                    },
                )
            },
            bottomBar = {
                Column {
                    if (current != null) PlayerBar(current, player, prefs) { nowPlaying = true }
                    NavigationBar {
                        TABS.forEachIndexed { i, name ->
                            NavigationBarItem(selected = tab == i, onClick = { tab = i; group = null }, icon = { Icon(TAB_ICONS[i], null) }, label = { Text(name) })
                        }
                    }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad)) {
                val list: @Composable (List<Song>, String, (@Composable () -> Unit)?) -> Unit = { l, name, header ->
                    SongList(
                        l, name, player, prefs, onEdit = { editing = it }, onDelete = ::requestDelete, onAddToPlaylist = { toPlaylist = it }, onInfo = { info = it },
                        onRemoveFromPlaylist = inPlaylist?.let { pl -> { s: Song -> prefs.setPlaylist(pl, prefs.playlists[pl].orEmpty() - s.id) } },
                        fastScroll = name == "Brani" && prefs.sort == "title", header = header,
                    )
                }
                // Album e artisti aperti hanno la testata con copertina, dati e tasti Riproduci / Casuale.
                val header: (@Composable () -> Unit)? =
                    if (g != null && g != FAV && g != RECENT && !g.startsWith(PL)) ({ GroupHeader(groupSongs, groupTitle!!, tab == 1, player, prefs) }) else null
                when {
                    !granted -> NoPermission { ask.launch(perms) }
                    tab == 4 -> SettingsScreen(prefs) { reloads++ }
                    group != null -> list(groupSongs, groupTitle!!, header)
                    tab == 3 -> LibraryScreen(prefs, songs) { group = it }
                    tab == 0 && songs.isEmpty() -> EmptyLibrary { reloads++ }
                    tab == 0 -> list(shown, if (q.isEmpty()) "Brani" else "Ricerca", null)
                    tab == 1 -> AlbumGrid(shown.groupBy { it.album }) { group = it }
                    else -> ArtistList(shown.groupBy { it.artist }) { group = it }
                }
            }
        }
        AnimatedVisibility(
            visible = nowPlaying && current != null,
            enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(),
        ) {
            player.current?.let { NowPlaying(it, player, prefs) { nowPlaying = false } }
        }
    }

    editing?.let { s ->
        val mp3 = remember(s.id) { ctx.contentResolver.getType(s.uri) == "audio/mpeg" }
        EditDialog(s, mp3, prefs.lyrics(s.id), onDismiss = { editing = null }, onWriteFile = { w -> editing = null; requestWrite(w) }) { t, a, al ->
            prefs.saveEdit(s.id, t, a, al); editing = null
        }
    }
    info?.let { s -> InfoDialog(s) { info = null } }
    update?.let { b ->
        AlertDialog(
            onDismissRequest = { update = null },
            title = { Text("Aggiornamento disponibile") },
            text = { Text("Su GitHub c'è una versione più recente di Musica (build $b, installata ${localBuild(ctx)}). Scaricarla?") },
            confirmButton = { TextButton({ ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(APK_URL))); update = null }) { Text("Scarica") } },
            dismissButton = {
                Row {
                    TextButton({ prefs.skipBuild = b; update = null }) { Text("Ignora") }
                    TextButton({ update = null }) { Text("Più tardi") }
                }
            },
        )
    }
    toPlaylist?.let { s -> AddToPlaylistDialog(s, prefs) { toPlaylist = null } }
    deleting?.takeIf { Build.VERSION.SDK_INT < 30 }?.let { s ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Eliminare il brano?") },
            text = { Text("\"${s.title}\" verrà cancellato dal dispositivo.") },
            confirmButton = { TextButton({ confirmedDelete(s) }) { Text("Elimina") } },
            dismissButton = { TextButton({ deleting = null }) { Text("Annulla") } },
        )
    }
}

@Composable
private fun SongList(
    songs: List<Song>, name: String, player: Player, prefs: Prefs,
    onEdit: (Song) -> Unit, onDelete: (Song) -> Unit, onAddToPlaylist: (Song) -> Unit, onInfo: (Song) -> Unit, onRemoveFromPlaylist: ((Song) -> Unit)? = null,
    fastScroll: Boolean = false, header: (@Composable () -> Unit)? = null,
) {
    if (songs.isEmpty()) return Center("Nessun brano")
    val ctx = LocalContext.current
    val state = rememberLazyListState()
    val scroller = fastScroll && songs.size >= 15
    Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize(), state, contentPadding = PaddingValues(bottom = 8.dp, end = if (scroller) 18.dp else 0.dp)) {
        if (header != null) item("header") { header() }
        itemsIndexed(songs, key = { i, s -> "${s.id}-$i" }) { _, s ->
            val isCurrent = s.id == player.current?.id
            val fav = s.id in prefs.favorites
            ListItem(
                headlineContent = {
                    Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (isCurrent) FontWeight.Bold else null,
                         color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Unspecified)
                },
                supportingContent = { Text("${s.artist} · ${s.album}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingContent = { AlbumArt(s, Modifier.size(52.dp), radius = 10.dp) },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (fav) Icon(Icons.Default.Favorite, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        if (prefs.showDuration) Text(fmt(s.duration), Modifier.padding(start = 6.dp), style = MaterialTheme.typography.bodySmall)
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "Altro") }
                            DropdownMenu(menu, { menu = false }) {
                                @Composable fun item(label: String, icon: ImageVector, act: () -> Unit) = DropdownMenuItem({ Text(label) }, { menu = false; act() }, leadingIcon = { Icon(icon, null) })
                                item("Riproduci dopo", IcPlaylistPlay) { player.playNext(s); Toast.makeText(ctx, "Suona dopo questo brano", Toast.LENGTH_SHORT).show() }
                                item("Aggiungi alla coda", IcPlaylistAdd) { player.enqueue(s); Toast.makeText(ctx, "Aggiunto alla coda", Toast.LENGTH_SHORT).show() }
                                item(if (fav) "Rimuovi dai preferiti" else "Aggiungi ai preferiti", if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder) { prefs.toggleFavorite(s.id) }
                                item("Aggiungi a playlist", IcLibrary) { onAddToPlaylist(s) }
                                if (onRemoveFromPlaylist != null) item("Rimuovi dalla playlist", Icons.Default.Close) { onRemoveFromPlaylist(s) }
                                item("Modifica", Icons.Default.Edit) { onEdit(s) }
                                item("Condividi", Icons.Default.Share) { share(ctx, s) }
                                item("Imposta come suoneria", Icons.Default.Phone) { setRingtone(ctx, s) }
                                item("Info file", Icons.Default.Info) { onInfo(s) }
                                item("Elimina dal dispositivo", Icons.Default.Delete) { onDelete(s) }
                            }
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent),
                modifier = Modifier.clickable { player.play(songs, s, name) },
            )
        }
    }
    if (scroller) AlphabetScroller(songs, state, Modifier.align(Alignment.CenterEnd))
    }
}

/** Striscia di lettere sul bordo destro: tocco o trascinamento saltano alla prima canzone con quell'iniziale. */
@Composable
private fun AlphabetScroller(songs: List<Song>, state: androidx.compose.foundation.lazy.LazyListState, modifier: Modifier) {
    val letters = remember(songs) { songs.map { letterOf(it.title) }.distinct() }
    var active by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun pick(y: Float, height: Int) {
        val l = letters[(y / height * letters.size).toInt().coerceIn(0, letters.size - 1)]
        active = l
        scope.launch { state.scrollToItem(songs.indexOfFirst { letterOf(it.title) == l }.coerceAtLeast(0)) }
    }
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier.padding(end = 2.dp)
                .pointerInput(letters) { detectTapGestures(onPress = { pick(it.y, size.height) }, onTap = { active = null }) }
                .pointerInput(letters) {
                    detectDragGestures(onDragEnd = { active = null }, onDragCancel = { active = null }) { change, _ -> change.consume(); pick(change.position.y, size.height) }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            letters.forEach { Text(it, Modifier.padding(horizontal = 4.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
        }
        active?.let {
            Box(Modifier.align(Alignment.Center).size(72.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Text(it, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

private fun letterOf(t: String) = t.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.let { if (it.isLetter()) it.toString() else "#" } ?: "#"

@Composable
private fun AlbumGrid(albums: Map<String, List<Song>>, onOpen: (String) -> Unit) {
    if (albums.isEmpty()) return Center("Nessun album")
    LazyVerticalGrid(
        GridCells.Adaptive(150.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(albums.keys.sortedBy { it.lowercase() }) { name ->
            val list = albums.getValue(name)
            Column(Modifier.clip(RoundedCornerShape(14.dp)).clickable { onOpen(name) }) {
                AlbumArt(list[0], Modifier.fillMaxWidth().aspectRatio(1f), radius = 14.dp)
                Text(name, Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${list[0].artist} · ${brani(list.size)}", Modifier.padding(start = 4.dp, end = 4.dp, bottom = 6.dp), style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ArtistList(artists: Map<String, List<Song>>, onOpen: (String) -> Unit) {
    if (artists.isEmpty()) return Center("Nessun artista")
    LazyColumn(Modifier.fillMaxSize()) {
        items(artists.keys.sortedBy { it.lowercase() }) { name ->
            val list = artists.getValue(name)
            ListItem(
                headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text("${list.map { it.album }.distinct().size} album · ${brani(list.size)}") },
                leadingContent = {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        Text(name.first().uppercase(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                },
                modifier = Modifier.clickable { onOpen(name) },
            )
        }
    }
}

@Composable
private fun LibraryScreen(prefs: Prefs, songs: List<Song>, onOpen: (String) -> Unit) {
    var newName by remember { mutableStateOf<String?>(null) }
    var deletingPl by remember { mutableStateOf<String?>(null) }
    val ids = remember(songs) { songs.map { it.id }.toSet() }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ListItem(
                headlineContent = { Text("Preferiti") }, supportingContent = { Text(brani(prefs.favorites.count { it in ids })) },
                leadingContent = { Icon(Icons.Default.Favorite, null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable { onOpen(FAV) },
            )
            ListItem(
                headlineContent = { Text("Ascoltati di recente") }, supportingContent = { Text(brani(prefs.recents.count { it in ids })) },
                leadingContent = { Icon(IcHistory, null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable { onOpen(RECENT) },
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Section("Playlist", Modifier.weight(1f))
                TextButton({ newName = "" }) { Text("Nuova") }
            }
        }
        items(prefs.playlists.keys.toList()) { name ->
            var menu by remember { mutableStateOf(false) }
            ListItem(
                headlineContent = { Text(name) }, supportingContent = { Text(brani(prefs.playlists[name].orEmpty().size)) },
                leadingContent = { Icon(IcQueue, null) },
                trailingContent = {
                    Box {
                        IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "Altro") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text("Elimina playlist") }, { menu = false; deletingPl = name }, leadingIcon = { Icon(Icons.Default.Delete, null) })
                        }
                    }
                },
                modifier = Modifier.clickable { onOpen(PL + name) },
            )
        }
        if (prefs.playlists.isEmpty()) item { Text("Nessuna playlist. Creane una da \"Nuova\" o dal menu di un brano.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    newName?.let { n ->
        AlertDialog(
            onDismissRequest = { newName = null },
            title = { Text("Nuova playlist") },
            text = { OutlinedTextField(n, { newName = it }, label = { Text("Nome") }, singleLine = true) },
            confirmButton = { TextButton({ prefs.setPlaylist(n.trim(), emptyList()); newName = null }, enabled = n.isNotBlank() && n.trim() !in prefs.playlists) { Text("Crea") } },
            dismissButton = { TextButton({ newName = null }) { Text("Annulla") } },
        )
    }
    deletingPl?.let { n ->
        AlertDialog(
            onDismissRequest = { deletingPl = null },
            title = { Text("Eliminare \"$n\"?") },
            text = { Text("I brani restano sul dispositivo.") },
            confirmButton = { TextButton({ prefs.setPlaylist(n, null); deletingPl = null }) { Text("Elimina") } },
            dismissButton = { TextButton({ deletingPl = null }) { Text("Annulla") } },
        )
    }
}

@Composable
private fun AddToPlaylistDialog(song: Song, prefs: Prefs, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var newName by remember { mutableStateOf("") }
    fun add(name: String) {
        val ids = prefs.playlists[name].orEmpty()
        if (song.id in ids) Toast.makeText(ctx, "Già presente in \"$name\"", Toast.LENGTH_SHORT).show()
        else { prefs.setPlaylist(name, ids + song.id); Toast.makeText(ctx, "Aggiunto a \"$name\"", Toast.LENGTH_SHORT).show() }
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aggiungi a playlist") },
        text = {
            Column {
                prefs.playlists.keys.forEach { name ->
                    ListItem(headlineContent = { Text(name) }, leadingContent = { Icon(IcQueue, null) }, modifier = Modifier.clickable { add(name) },
                             colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                }
                OutlinedTextField(newName, { newName = it }, label = { Text("Nuova playlist") }, singleLine = true, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton({ add(newName.trim()) }, enabled = newName.isNotBlank()) { Text("Crea e aggiungi") } },
        dismissButton = { TextButton(onDismiss) { Text("Annulla") } },
    )
}

@Composable
private fun PlayerBar(song: Song, player: Player, prefs: Prefs, onOpen: () -> Unit) {
    val pos = rememberPosition(song, player)
    val haptic = rememberHaptic(prefs)
    val thr = LocalDensity.current.run { 64.dp.toPx() }
    var dx by remember { mutableFloatStateOf(0f) }
    var dy by remember { mutableFloatStateOf(0f) }
    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier.clickable(onClick = onOpen).pointerInput(Unit) {
            // Gesti: swipe su apre In riproduzione, swipe laterale cambia brano.
            detectDragGestures(onDragEnd = {
                when { dy < -thr -> onOpen(); dx > thr -> { haptic(); player.prev() }; dx < -thr -> { haptic(); player.next() } }
                dx = 0f; dy = 0f
            }) { change, drag -> change.consume(); dx += drag.x; dy += drag.y }
        },
    ) {
        Column {
            LinearProgressIndicator(
                progress = { pos / song.duration.toFloat().coerceAtLeast(1f) },
                modifier = Modifier.fillMaxWidth().height(2.dp), drawStopIndicator = {},
            )
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AlbumArt(song, Modifier.size(48.dp), radius = 10.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(song.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton({ haptic(); player.prev() }) { Icon(IcPrev, "Precedente") }
                FilledIconButton({ haptic(); player.toggle() }) { Icon(if (player.playing) IcPause else Icons.Default.PlayArrow, "Play/Pausa") }
                IconButton({ haptic(); player.next() }) { Icon(IcNext, "Successivo") }
            }
        }
    }
}

@Composable
private fun NowPlaying(song: Song, player: Player, prefs: Prefs, onClose: () -> Unit) {
    var showQueue by remember { mutableStateOf(false) }
    var showEq by remember { mutableStateOf(false) }
    BackHandler { when { showQueue -> showQueue = false; showEq -> showEq = false; else -> onClose() } }
    if (showQueue) return QueueScreen(player) { showQueue = false }
    if (showEq) return EqualizerScreen(player.effects, prefs) { showEq = false }
    val ctx = LocalContext.current
    val haptic = rememberHaptic(prefs)
    val art by produceState(cachedArt(song), song.id) { value = cachedArt(song) ?: withContext(Dispatchers.IO) { ctx.contentResolver.albumArt(song) } }
    val primary = MaterialTheme.colorScheme.primary
    val tint = remember(art) { art?.let { Color(it.averageColor()) } ?: primary }
    var showLyrics by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var sleepDialog by remember { mutableStateOf(false) }
    var speedDialog by remember { mutableStateOf(false) }
    var swipe by remember { mutableFloatStateOf(0f) }
    val thr = LocalDensity.current.run { 64.dp.toPx() }
    val fav = song.id in prefs.favorites
    val micOk = ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) { prefs.visualizer = true; player.setVisualizer(true) } }
    DisposableEffect(prefs.visualizer) {
        if (prefs.visualizer && micOk) player.setVisualizer(true)
        onDispose { player.setVisualizer(false) }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // Sfondo: copertina sfocata (Android 12+), altrimenti sfumatura del suo colore medio.
        if (Build.VERSION.SDK_INT >= 31 && art != null) {
            Image(art!!.asImageBitmap(), null, Modifier.fillMaxSize().blur(60.dp), contentScale = ContentScale.Crop, alpha = 0.5f)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 1f to MaterialTheme.colorScheme.surface)))
        } else {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to tint.copy(alpha = 0.55f), 0.65f to Color.Transparent)))
        }
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("In riproduzione") },
                    navigationIcon = { IconButton(onClose) { Icon(Icons.Default.KeyboardArrowDown, "Chiudi") } },
                    actions = {
                        IconToggle(if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, fav, "Preferito") { prefs.toggleFavorite(song.id) }
                        IconToggle(IcLyrics, showLyrics, "Testo") { showLyrics = it }
                        IconButton({ showQueue = true }) { Icon(IcQueue, "Coda") }
                        Box {
                            IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "Altro") }
                            DropdownMenu(menu, { menu = false }) {
                                val left = player.sleepEndsAt?.let { ((it - System.currentTimeMillis()) / 60_000 + 1).coerceAtLeast(1) }
                                DropdownMenuItem({ Text(when { player.sleepAtEnd -> "Timer: a fine brano"; left != null -> "Timer: $left min"; else -> "Timer di spegnimento" }) }, { menu = false; sleepDialog = true }, leadingIcon = { Icon(IcTimer, null) })
                                DropdownMenuItem({ Text("Velocità: ${speedLabel(prefs.speed)}") }, { menu = false; speedDialog = true }, leadingIcon = { Icon(IcSpeed, null) })
                                DropdownMenuItem({ Text("Equalizzatore") }, { menu = false; showEq = true }, leadingIcon = { Icon(IcEqualizer, null) })
                                DropdownMenuItem({ Text(if (prefs.visualizer) "Nascondi visualizzatore" else "Visualizzatore") }, {
                                    menu = false
                                    when {
                                        prefs.visualizer -> { prefs.visualizer = false; player.setVisualizer(false) }
                                        micOk -> { prefs.visualizer = true; player.setVisualizer(true) }
                                        else -> mic.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }, leadingIcon = { Icon(IcVisualizer, null) })
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
        ) { pad ->
            val visual: @Composable (Modifier) -> Unit = { m ->
                Box(m, contentAlignment = Alignment.Center) {
                    if (showLyrics) Lyrics(song, player, prefs, Modifier.fillMaxSize())
                    else ArtImage(
                        art,
                        Modifier.aspectRatio(1f).shadow(16.dp, RoundedCornerShape(20.dp))
                            // Gesti sulla copertina: tocco = testo, doppio tocco = preferito, scorrimento laterale = cambia brano.
                            .pointerInput(song.id) { detectTapGestures(onTap = { showLyrics = true }, onDoubleTap = { haptic(); prefs.toggleFavorite(song.id) }) }
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures(onDragEnd = {
                                    when { swipe > thr -> { haptic(); player.prev() }; swipe < -thr -> { haptic(); player.next() } }
                                    swipe = 0f
                                }) { change, d -> change.consume(); swipe += d }
                            },
                        radius = 20.dp,
                    )
                }
            }
            val controls: @Composable () -> Unit = {
                if (prefs.visualizer && player.spectrum.isNotEmpty()) Spectrum(player.spectrum, Modifier.fillMaxWidth().height(48.dp).padding(bottom = 8.dp))
                Text(song.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${song.artist} · ${song.album}", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
                SeekBar(song, player)
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
                    IconToggle(IcShuffle, prefs.shuffle, "Casuale") { prefs.shuffle = it }
                    IconButton({ haptic(); player.prev() }, Modifier.size(56.dp)) { Icon(IcPrev, "Precedente", Modifier.size(36.dp)) }
                    FilledIconButton({ haptic(); player.toggle() }, Modifier.size(72.dp)) {
                        Icon(if (player.playing) IcPause else Icons.Default.PlayArrow, "Play/Pausa", Modifier.size(40.dp))
                    }
                    IconButton({ haptic(); player.next() }, Modifier.size(56.dp)) { Icon(IcNext, "Successivo", Modifier.size(36.dp)) }
                    IconToggle(IcRepeat, prefs.repeat, "Ripeti") { prefs.repeat = it }
                }
            }
            BoxWithConstraints(Modifier.padding(pad).fillMaxSize()) {
                if (maxWidth > maxHeight) Row(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    visual(Modifier.weight(1f).fillMaxSize().padding(8.dp))
                    Column(Modifier.weight(1f).padding(start = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) { controls() }
                } else Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    visual(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp))
                    controls()
                }
            }
        }
    }

    if (sleepDialog) AlertDialog(
        onDismissRequest = { sleepDialog = false },
        title = { Text("Timer di spegnimento") },
        text = {
            Column {
                (listOf<Int?>(null) + SLEEP).forEach { m ->
                    ListItem(headlineContent = { Text(m?.let { "$it minuti" } ?: "Spento") }, colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                             modifier = Modifier.clickable { player.setSleep(m); sleepDialog = false })
                }
                ListItem(headlineContent = { Text("A fine brano") }, colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                         modifier = Modifier.clickable { player.stopAtEnd(true); sleepDialog = false })
            }
        },
        confirmButton = { TextButton({ sleepDialog = false }) { Text("Chiudi") } },
    )
    if (speedDialog) AlertDialog(
        onDismissRequest = { speedDialog = false },
        title = { Text("Velocità di riproduzione") },
        text = {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SPEEDS.forEach { s -> FilterChip(selected = prefs.speed == s, onClick = { player.setSpeed(s) }, label = { Text(speedLabel(s)) }) }
            }
        },
        confirmButton = { TextButton({ speedDialog = false }) { Text("Chiudi") } },
    )
}

@Composable
private fun EqualizerScreen(fx: Effects, prefs: Prefs, onClose: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Equalizzatore") }, navigationIcon = { IconButton(onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } }) }) { pad ->
        if (!fx.available) return@Scaffold Box(Modifier.padding(pad)) { Center("Equalizzatore non disponibile su questo dispositivo") }
        fx.version // letto per ricomporre a ogni modifica
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Toggle("Attivo", prefs.eqEnabled) { fx.setEnabled(it) }
            Text("Preset", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = prefs.eqPreset < 0, onClick = {}, label = { Text("Personalizzato") })
                fx.presets.forEachIndexed { i, n -> FilterChip(selected = prefs.eqPreset == i, onClick = { fx.setPreset(i) }, label = { Text(n) }) }
            }
            Section("Bande")
            for (i in 0 until fx.bands) {
                val hz = fx.bandFreqHz(i)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz", Modifier.width(64.dp), style = MaterialTheme.typography.labelMedium)
                    Slider(fx.level(i).toFloat(), { fx.setBand(i, it.toInt()) }, Modifier.weight(1f), enabled = prefs.eqEnabled,
                           valueRange = fx.range.first.toFloat()..fx.range.last.toFloat())
                    Text("%+d dB".format(fx.level(i) / 100), Modifier.width(52.dp), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
                }
            }
            Section("Bass boost")
            Slider(prefs.bassBoost.toFloat(), { fx.setBass(it.toInt()) }, enabled = prefs.eqEnabled, valueRange = 0f..1000f)
        }
    }
}

@Composable
private fun QueueScreen(player: Player, onClose: () -> Unit) {
    val state = rememberLazyListState()
    var dragging by remember { mutableIntStateOf(-1) }   // indice in upNext del brano trascinato
    var dragY by remember { mutableFloatStateOf(0f) }
    val base = (if (player.current != null) 2 else 0) + 1  // posizione del primo brano di upNext nella LazyColumn
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Coda") },
                navigationIcon = { IconButton(onClose) { Icon(Icons.Default.KeyboardArrowDown, "Chiudi") } },
                actions = { if (player.upNext.isNotEmpty()) TextButton(player::clearUpNext) { Text("Svuota coda") } },
            )
        },
    ) { pad ->
        // ponytail: niente riordino col trascinamento; rimozione e salto al brano bastano per ora.
        LazyColumn(Modifier.padding(pad).fillMaxSize(), state, contentPadding = PaddingValues(bottom = 16.dp)) {
            player.current?.let { item { Section("In riproduzione", Modifier.padding(horizontal = 16.dp)) }; item { QueueRow(it, true) } }
            if (player.upNext.isNotEmpty()) {
                item { Section("Prossimi in coda", Modifier.padding(horizontal = 16.dp)) }
                itemsIndexed(player.upNext) { i, s ->
                    QueueRow(
                        s, onClick = { player.playUpNext(i) }, onRemove = { player.removeUpNext(i) },
                        modifier = Modifier.zIndex(if (i == dragging) 1f else 0f).graphicsLayer { translationY = if (i == dragging) dragY else 0f },
                        handle = Modifier.pointerInput(Unit) {
                            // Trascina la maniglia: quando il centro del brano entra in un'altra riga della coda, le due si scambiano.
                            detectDragGestures(
                                onDragStart = { dragging = i; dragY = 0f },
                                onDragEnd = { dragging = -1; dragY = 0f }, onDragCancel = { dragging = -1; dragY = 0f },
                            ) { change, drag ->
                                change.consume()
                                dragY += drag.y
                                val items = state.layoutInfo.visibleItemsInfo
                                val me = items.firstOrNull { it.index == base + dragging } ?: return@detectDragGestures
                                val center = me.offset + dragY + me.size / 2f
                                val target = items.firstOrNull { it.index != me.index && it.index in base until base + player.upNext.size && center >= it.offset && center < it.offset + it.size }
                                    ?: return@detectDragGestures
                                player.moveUpNext(dragging, target.index - base)
                                dragY += me.offset - target.offset
                                dragging = target.index - base
                            }
                        },
                    )
                }
            }
            val rest = player.nextInContext
            if (rest.isNotEmpty()) {
                item { Section("Prossimi da: ${player.queueName}", Modifier.padding(horizontal = 16.dp)) }
                items(rest, key = { it.id }) { s -> QueueRow(s, onClick = { player.playInContext(s) }) }
            }
            if (player.upNext.isEmpty() && rest.isEmpty()) item { Text("Niente in coda", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun QueueRow(
    s: Song, highlight: Boolean = false, onClick: (() -> Unit)? = null, onRemove: (() -> Unit)? = null,
    modifier: Modifier = Modifier, handle: Modifier? = null,
) {
    ListItem(
        headlineContent = { Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (highlight) FontWeight.Bold else null, color = if (highlight) MaterialTheme.colorScheme.primary else Color.Unspecified) },
        supportingContent = { Text(s.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { AlbumArt(s, Modifier.size(44.dp), radius = 8.dp) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onRemove != null) IconButton(onRemove) { Icon(Icons.Default.Close, "Rimuovi") }
                if (handle != null) Icon(IcDragHandle, "Trascina per riordinare", handle.padding(8.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        colors = ListItemDefaults.colors(containerColor = if (handle != null && modifier != Modifier) MaterialTheme.colorScheme.surface else Color.Transparent),
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
    )
}

/** Barre dello spettro audio. */
@Composable
private fun Spectrum(bars: FloatArray, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val w = size.width / bars.size
        bars.forEachIndexed { i, v ->
            val h = (size.height * v).coerceAtLeast(3.dp.toPx())
            drawRoundRect(color, topLeft = Offset(i * w + w * 0.15f, size.height - h), size = Size(w * 0.7f, h), cornerRadius = CornerRadius(w * 0.35f))
        }
    }
}

@Composable
private fun Lyrics(song: Song, player: Player, prefs: Prefs, modifier: Modifier) {
    val ctx = LocalContext.current
    val raw by produceState<String?>(null, song.id) {
        value = withContext(Dispatchers.IO) {
            prefs.lyrics(song.id)
                ?: (ctx.contentResolver.embeddedLyrics(song.uri) ?: fetchLyrics(song))?.also { prefs.saveLyrics(song.id, it) }
                ?: ""
        }
    }
    val text = raw
    val lines = remember(text) { parseLrc(text.orEmpty()) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when {
        text == null -> Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        text.isBlank() -> Box(modifier, contentAlignment = Alignment.Center) { Text("Testo non disponibile", color = muted) }
        lines[0].time == null -> Column(modifier.verticalScroll(rememberScrollState())) {
            Text(text, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge, lineHeight = 28.sp, textAlign = TextAlign.Center)
        }
        else -> {
            val pos = rememberPosition(song, player)
            val active = lines.indexOfLast { it.time!! <= pos }
            val state = rememberLazyListState()
            LaunchedEffect(active) { if (active >= 0) state.animateScrollToItem(active, -state.layoutInfo.viewportSize.height / 3) }
            LazyColumn(modifier, state, contentPadding = PaddingValues(vertical = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                itemsIndexed(lines) { i, l ->
                    val on = i == active
                    Text(
                        l.text.ifBlank { "♪" },
                        Modifier.fillMaxWidth().clickable { player.seekTo(l.time!!.toInt()) }.padding(vertical = 6.dp),
                        style = if (on) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                        fontWeight = if (on) FontWeight.Bold else null,
                        color = if (on) MaterialTheme.colorScheme.primary else muted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Posizione corrente del player, aggiornata ogni 300 ms. */
@Composable
private fun rememberPosition(song: Song, player: Player): Int {
    var pos by remember { mutableIntStateOf(0) }
    LaunchedEffect(song, player.playing) { while (true) { pos = player.position; delay(300) } }
    return pos
}

@Composable
private fun rememberHaptic(prefs: Prefs): () -> Unit {
    val view = LocalView.current
    return { if (prefs.haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
}

@Composable
private fun SeekBar(song: Song, player: Player) {
    val pos = rememberPosition(song, player)
    var drag by remember { mutableFloatStateOf(-1f) }
    val value = if (drag >= 0) drag else pos.toFloat()
    Column {
        Slider(
            value = value,
            onValueChange = { drag = it },
            onValueChangeFinished = { player.seekTo(drag.toInt()); drag = -1f },
            valueRange = 0f..song.duration.toFloat().coerceAtLeast(1f),
        )
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text(fmt(value.toLong()), style = MaterialTheme.typography.labelSmall)
            Text(fmt(song.duration), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun AlbumArt(song: Song, modifier: Modifier, radius: Dp = 8.dp) {
    val ctx = LocalContext.current
    // Al cambio di chiave produceState rilancia il blocco ma tiene il valore vecchio: va riassegnato sempre.
    val bmp by produceState(cachedArt(song), song.id) { value = cachedArt(song) ?: withContext(Dispatchers.IO) { ctx.contentResolver.albumArt(song) } }
    ArtImage(bmp, modifier, radius)
}

@Composable
private fun ArtImage(bmp: Bitmap?, modifier: Modifier, radius: Dp) {
    Box(modifier.clip(RoundedCornerShape(radius)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(IcMusicNote, null, Modifier.fillMaxSize(0.5f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsScreen(prefs: Prefs, onReload: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Section("Aspetto")
        Choice("Tema", listOf("system" to "Sistema", "light" to "Chiaro", "dark" to "Scuro", "amoled" to "Nero"), prefs.theme) { prefs.theme = it }
        if (Build.VERSION.SDK_INT >= 31) Toggle("Colori dinamici (Material You)", prefs.dynamic) { prefs.dynamic = it }
        if (!prefs.dynamic || Build.VERSION.SDK_INT < 31) {
            Text("Colore accento", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ACCENTS.forEach { (name, color) ->
                    val selected = prefs.accent == name
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(color)
                            .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            .clickable { prefs.accent = name },
                        contentAlignment = Alignment.Center,
                    ) { if (selected) Icon(Icons.Default.Check, name, tint = Color.White) }
                }
            }
        }
        Toggle("Vibrazione sui tasti", prefs.haptics) { prefs.haptics = it }
        Section("Libreria")
        Choice("Ordina brani per", listOf("title" to "Titolo", "artist" to "Artista", "album" to "Album", "date" to "Più recenti"), prefs.sort) { prefs.sort = it }
        Toggle("Mostra durata", prefs.showDuration) { prefs.showDuration = it }
        OutlinedButton(onReload) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Ricarica libreria") }
        Section("Riproduzione")
        Toggle("Riproduzione casuale", prefs.shuffle) { prefs.shuffle = it }
        Toggle("Ripeti la coda", prefs.repeat) { prefs.repeat = it }
        Choice("Dissolvenza incrociata", listOf("0" to "Spenta", "3" to "3 s", "6" to "6 s", "10" to "10 s"), prefs.crossfade.toString()) { prefs.crossfade = it.toInt() }
        Section("Notifiche e lock screen")
        val enabled = ctx.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        Text(
            if (enabled) "Notifiche attive: i controlli compaiono nella tendina, su lock screen e nel pannello media."
            else "Notifiche disattivate: senza, Android non mostra i controlli su lock screen, pannello media o isola.",
            style = MaterialTheme.typography.bodySmall, color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({
                ctx.startActivity(
                    if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                    else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                )
            }) { Text("Notifiche app") }
            OutlinedButton({ ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))) }) { Text("Info app") }
        }
        val crashFile = File(ctx.filesDir, "crash.log")
        var crash by remember { mutableStateOf(crashFile.takeIf { it.exists() }?.readText()) }
        crash?.let { log ->
            Section("Diagnostica")
            Text("Ultimo crash: ${log.lineSequence().first()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, log), "Condividi log")) }) {
                    Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text("Condividi")
                }
                OutlinedButton({ crashFile.delete(); crash = null }) { Text("Cancella") }
            }
        }
        Section("Info")
        Text("Musica ${localVersion(ctx)} (build ${localBuild(ctx)}) · Gli aggiornamenti vengono cercati su GitHub a ogni avvio. Testi: tag del file o lrclib.net.", style = MaterialTheme.typography.bodySmall)
        val scope = rememberCoroutineScope()
        OutlinedButton({
            scope.launch {
                val r = withContext(Dispatchers.IO) { fetchLatestBuild() }
                val msg = when {
                    r.error != null -> "Controllo non riuscito: ${r.error}"
                    r.build!! > localBuild(ctx) -> "Disponibile la build ${r.build}: scarica da GitHub"
                    else -> "Sei già alla versione più recente (build ${r.build})"
                }
                Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                if (r.error == null && r.build!! > localBuild(ctx)) ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(APK_URL)))
            }
        }) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Controlla aggiornamenti") }
    }
}

private data class TagWrite(
    val song: Song, val title: String, val artist: String, val album: String,
    val genre: String = "", val year: String = "", val track: String = "", val lyrics: String? = null, val cover: ByteArray? = null,
)

@Composable
private fun EditDialog(song: Song, canWriteFile: Boolean, lyrics: String?, onDismiss: () -> Unit, onWriteFile: (TagWrite) -> Unit, onSave: (String?, String?, String?) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var t by remember { mutableStateOf(song.title) }
    var a by remember { mutableStateOf(song.artist) }
    var al by remember { mutableStateOf(song.album) }
    var genre by remember { mutableStateOf(song.genre) }
    var year by remember { mutableStateOf(song.year.takeIf { it > 0 }?.toString().orEmpty()) }
    var track by remember { mutableStateOf((song.track % 1000).takeIf { it > 0 }?.toString().orEmpty()) }
    var cover by remember { mutableStateOf<ByteArray?>(null) }
    var withLyrics by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            cover = withContext(Dispatchers.IO) { loadCover(ctx, uri) }
            if (cover == null) Toast.makeText(ctx, "Immagine non leggibile", Toast.LENGTH_SHORT).show()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifica brano") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(t, { t = it }, label = { Text("Titolo") }, singleLine = true)
                OutlinedTextField(a, { a = it }, label = { Text("Artista") }, singleLine = true)
                OutlinedTextField(al, { al = it }, label = { Text("Album") }, singleLine = true)
                TextButton({ onSave(null, null, null) }) { Text("Ripristina originale") }
                if (canWriteFile) {
                    Text("\"Salva\" tiene titolo, artista e album solo in questa app. \"Scrivi nel file\" aggiorna i tag ID3 del file MP3 con tutti i campi qui sotto, visibili anche alle altre app.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(genre, { genre = it }, label = { Text("Genere") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f), label = { Text("Anno") }, singleLine = true)
                        OutlinedTextField(track, { track = it.filter(Char::isDigit).take(3) }, Modifier.weight(1f), label = { Text("Traccia") }, singleLine = true)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val bmp = remember(cover) { cover?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
                        if (bmp != null) ArtImage(bmp, Modifier.size(48.dp), radius = 8.dp)
                        TextButton({ pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text(if (cover == null) "Scegli copertina" else "Cambia copertina") }
                        if (cover != null) TextButton({ cover = null }) { Text("Togli") }
                    }
                    if (lyrics != null) Toggle("Scrivi anche il testo nel file", withLyrics) { withLyrics = it }
                    TextButton({ onWriteFile(TagWrite(song, t.trim(), a.trim(), al.trim(), genre.trim(), year, track, lyrics.takeIf { withLyrics }, cover)) }, enabled = t.isNotBlank()) { Text("Scrivi nel file MP3") }
                }
            }
        },
        confirmButton = { TextButton({ onSave(t.trim(), a.trim(), al.trim()) }, enabled = t.isNotBlank()) { Text("Salva") } },
        dismissButton = { TextButton(onDismiss) { Text("Annulla") } },
    )
}

@Composable
private fun InfoDialog(song: Song, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val rows by produceState(emptyList<Pair<String, String>>(), song.id) { value = withContext(Dispatchers.IO) { fileInfo(ctx, song) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Info file") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                rows.forEach { (k, v) ->
                    Column {
                        Text(k, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(v, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Chiudi") } },
    )
}

/** Testata di album o artista: copertina grande, dati e tasti Riproduci / Casuale. */
@Composable
private fun GroupHeader(songs: List<Song>, title: String, isAlbum: Boolean, player: Player, prefs: Prefs) {
    if (songs.isEmpty()) return
    val total = songs.sumOf { it.duration }
    val h = total / 3_600_000; val m = total / 60_000 % 60
    val durata = when { h > 0 -> "$h h $m min"; m > 0 -> "$m min"; else -> "${total / 1000} s" }
    val year = songs.maxOf { it.year }
    val sub = if (isAlbum) listOfNotNull(songs[0].artist, year.takeIf { it > 0 }?.toString(), brani(songs.size), durata).joinToString(" · ")
              else "${songs.map { it.album }.distinct().size} album · ${brani(songs.size)} · $durata"
    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (isAlbum) AlbumArt(songs[0], Modifier.size(180.dp).shadow(12.dp, RoundedCornerShape(20.dp)), radius = 20.dp)
        else Box(Modifier.size(140.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Text(title.firstOrNull()?.uppercase() ?: "?", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(title, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button({ player.play(songs, songs[0], title) }) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Riproduci") }
            OutlinedButton({ prefs.shuffle = true; player.play(songs, songs.random(), title) }) { Icon(IcShuffle, null); Spacer(Modifier.width(6.dp)); Text("Casuale") }
        }
    }
}

@Composable
private fun NoPermission(ask: () -> Unit) {
    Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
        Text("Serve il permesso per leggere la musica sul dispositivo")
        Spacer(Modifier.height(8.dp))
        Button(ask) { Text("Concedi") }
    }
}

@Composable
private fun EmptyLibrary(onReload: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Icon(IcMusicNote, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Nessun brano trovato", style = MaterialTheme.typography.titleLarge)
        Text("Copia i file audio nella cartella Musica del telefono, o in una sua sottocartella: compariranno qui.",
             Modifier.padding(top = 8.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Button(onReload) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Ricarica") }
    }
}

@Composable
private fun Center(text: String) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text) }

@Composable
private fun Section(title: String, modifier: Modifier = Modifier) =
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = modifier.padding(top = 8.dp))

@Composable
private fun Toggle(label: String, on: Boolean, set: (Boolean) -> Unit) =
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(on, set) }

@Composable
private fun IconToggle(icon: ImageVector, on: Boolean, desc: String, set: (Boolean) -> Unit) =
    IconButton(
        { set(!on) },
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            contentColor = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) { Icon(icon, desc) }

@Composable
private fun Choice(label: String, options: List<Pair<String, String>>, value: String, set: (String) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (k, v) -> FilterChip(selected = value == k, onClick = { set(k) }, label = { Text(v) }) }
    }
}

private fun fmt(ms: Long) = "%d:%02d".format(ms / 60000, ms / 1000 % 60)
@Suppress("DEPRECATION")
private fun localBuild(ctx: Context): Int = ctx.packageManager.getPackageInfo(ctx.packageName, 0).let { if (Build.VERSION.SDK_INT >= 28) it.longVersionCode.toInt() else it.versionCode }
private fun localVersion(ctx: Context): String = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
private fun brani(n: Int) = if (n == 1) "1 brano" else "$n brani"

private fun share(ctx: Context, s: Song) = ctx.startActivity(Intent.createChooser(
    Intent(Intent.ACTION_SEND).setType(ctx.contentResolver.getType(s.uri) ?: "audio/*").putExtra(Intent.EXTRA_STREAM, s.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
    "Condividi brano",
))

/** Serve il permesso "Modifica impostazioni di sistema": la prima volta apre la schermata di sistema per concederlo. */
private fun setRingtone(ctx: Context, s: Song) {
    if (!Settings.System.canWrite(ctx)) {
        Toast.makeText(ctx, "Consenti a Musica di modificare le impostazioni, poi riprova", Toast.LENGTH_LONG).show()
        ctx.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}")))
        return
    }
    runCatching { RingtoneManager.setActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_RINGTONE, s.uri) }
        .onSuccess { Toast.makeText(ctx, "Suoneria impostata: ${s.title}", Toast.LENGTH_SHORT).show() }
        .onFailure { Toast.makeText(ctx, "Non riuscito: ${it.message}", Toast.LENGTH_LONG).show() }
}

/** Immagine scelta dalla galleria → JPEG di al più 1000 px per il frame APIC. */
private fun loadCover(ctx: Context, uri: Uri): ByteArray? = runCatching {
    val src = ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) } ?: return null
    val k = 1000f / maxOf(src.width, src.height)
    val b = if (k < 1) Bitmap.createScaledBitmap(src, (src.width * k).toInt(), (src.height * k).toInt(), true) else src
    java.io.ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 88, it) }.toByteArray()
}.getOrNull()
private fun speedLabel(s: Float) = if (s == s.toLong().toFloat()) "${s.toLong()}×" else "$s×"

// Icone Material non incluse in icons-core, ricostruite dal path (evita la dipendenza icons-extended).
private fun vec(d: String) = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    .addPath(PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.Black)).build()
private val IcPause = vec("M6 19h4V5H6v14zm8-14v14h4V5h-4z")
private val IcNext = vec("M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z")
private val IcPrev = vec("M6 6h2v12H6zm3.5 6l8.5 6V6z")
private val IcMusicNote = vec("M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z")
private val IcAlbum = vec("M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 14.5c-2.49 0-4.5-2.01-4.5-4.5S9.51 7.5 12 7.5s4.5 2.01 4.5 4.5-2.01 4.5-4.5 4.5zm0-5.5c-.55 0-1 .45-1 1s.45 1 1 1 1-.45 1-1-.45-1-1-1z")
private val IcShuffle = vec("M10.59 9.17L5.41 4 4 5.41l5.17 5.17 1.42-1.41zM14.5 4l2.04 2.04L4 18.59 5.41 20 17.96 7.46 20 9.5V4h-5.5zm.33 9.41l-1.41 1.41 3.13 3.13L14.5 20H20v-5.5l-2.04 2.04-3.13-3.13z")
private val IcRepeat = vec("M7 7h10v3l4-4-4-4v3H5v6h2V7zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2v4z")
private val IcQueue = vec("M15 6H3v2h12V6zm0 4H3v2h12v-2zM3 16h8v-2H3v2zM17 6v8.18c-.31-.11-.65-.18-1-.18-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3V8h3V6h-5z")
private val IcPlaylistAdd = vec("M14 10H3v2h11v-2zm0-4H3v2h11V6zm4 8v-4h-2v4h-4v2h4v4h2v-4h4v-2h-4zM3 16h7v-2H3v2z")
private val IcPlaylistPlay = vec("M19 9H2v2h17V9zm0-4H2v2h17V5zM2 15h13v-2H2v2zm15-2v6l5-3-5-3z")
private val IcLyrics = vec("M14 17H4v2h10v-2zm6-8H4v2h16V9zM4 15h16v-2H4v2zM4 5v2h16V5H4z")
private val IcLibrary = vec("M20 2H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-2 5h-3v5.5c0 1.38-1.12 2.5-2.5 2.5S10 13.88 10 12.5s1.12-2.5 2.5-2.5c.57 0 1.08.19 1.5.51V5h4v2zM4 6H2v14c0 1.1.9 2 2 2h14v-2H4V6z")
private val IcHistory = vec("M13 3c-4.97 0-9 4.03-9 9H1l3.89 3.89.07.14L9 12H6c0-3.87 3.13-7 7-7s7 3.13 7 7-3.13 7-7 7c-1.93 0-3.68-.79-4.94-2.06l-1.42 1.42C8.27 19.99 10.51 21 13 21c4.97 0 9-4.03 9-9s-4.03-9-9-9zm-1 5v5l4.28 2.54.72-1.21-3.5-2.08V8H12z")
private val IcTimer = vec("M15 1H9v2h6V1zm-4 13h2V8h-2v6zm8.03-6.61l1.42-1.42c-.43-.51-.9-.99-1.41-1.41l-1.42 1.42C16.07 4.74 14.12 4 12 4c-4.97 0-9 4.03-9 9s4.02 9 9 9 9-4.03 9-9c0-2.12-.74-4.07-1.97-5.61zM12 20c-3.87 0-7-3.13-7-7s3.13-7 7-7 7 3.13 7 7-3.13 7-7 7z")
private val IcSpeed = vec("M20.38 8.57l-1.23 1.85a8 8 0 0 1-.22 7.58H5.07A8 8 0 0 1 15.58 6.85l1.85-1.23A10 10 0 0 0 3.35 19a2 2 0 0 0 1.72 1h13.85a2 2 0 0 0 1.74-1 10 10 0 0 0-.27-10.44zm-9.79 6.84a2 2 0 0 0 2.83 0l5.66-8.49-8.49 5.66a2 2 0 0 0 0 2.83z")
private val IcEqualizer = vec("M10 20h4V4h-4v16zm-6 0h4v-8H4v8zM16 9v11h4V9h-4z")
private val IcVisualizer = vec("M7 18h2V6H7v12zm4 4h2V2h-2v20zm-8-8h2v-4H3v4zm12 4h2V6h-2v12zm4-8v4h2v-4h-2z")
private val IcDragHandle = vec("M20 9H4v2h16V9zM4 15h16v-2H4v2z")
private val TAB_ICONS = listOf(IcMusicNote, IcAlbum, Icons.Default.Person, IcLibrary, Icons.Default.Settings)
