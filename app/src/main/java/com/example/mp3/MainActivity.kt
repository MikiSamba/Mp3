@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.mp3

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val ACCENTS = linkedMapOf(
    "Arancio" to Color(0xFFFB8C00), "Rosso" to Color(0xFFE53935), "Blu" to Color(0xFF1E88E5),
    "Verde" to Color(0xFF43A047), "Viola" to Color(0xFF8E24AA), "Rosa" to Color(0xFFD81B60),
)
private val TABS = listOf("Brani", "Album", "Artisti", "Impostazioni")

class MainActivity : ComponentActivity() {
    private lateinit var player: Player

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = Prefs(this)
        player = Player(this, prefs)
        setContent { Themed(prefs) { App(prefs, player) } }
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}

@Composable
private fun Themed(prefs: Prefs, content: @Composable () -> Unit) {
    val dark = when (prefs.theme) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    val accent = ACCENTS[prefs.accent] ?: ACCENTS.values.first()
    val scheme = when {
        prefs.dynamic && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(primary = accent, primaryContainer = accent.copy(alpha = 0.3f))
        else -> lightColorScheme(primary = accent, primaryContainer = accent.copy(alpha = 0.15f))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
private fun App(prefs: Prefs, player: Player) {
    val ctx = LocalContext.current
    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember { mutableStateOf(ctx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(perm) }

    var raw by remember { mutableStateOf(emptyList<Song>()) }
    LaunchedEffect(granted) { if (granted) raw = withContext(Dispatchers.IO) { ctx.contentResolver.loadSongs() } }
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
    LaunchedEffect(songs) { player.refresh(songs) }

    var tab by remember { mutableIntStateOf(0) }
    var group by remember { mutableStateOf<String?>(null) }   // album o artista aperto
    var query by remember { mutableStateOf<String?>(null) }   // null = ricerca chiusa
    var editing by remember { mutableStateOf<Song?>(null) }
    BackHandler(group != null || query != null) { group = null; query = null }

    val shown = remember(songs, query) {
        val q = query.orEmpty().trim()
        if (q.isEmpty()) songs else songs.filter { s -> listOf(s.title, s.artist, s.album).any { it.contains(q, ignoreCase = true) } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val q = query
                    if (q != null) TextField(q, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Cerca…") }, singleLine = true)
                    else Text(group ?: if (tab == 0) "Musica" else TABS[tab])
                },
                navigationIcon = { if (group != null) IconButton({ group = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
                actions = {
                    if (tab < 3) IconButton({ query = if (query == null) "" else null }) {
                        Icon(if (query == null) Icons.Default.Search else Icons.Default.Close, "Cerca")
                    }
                },
            )
        },
        bottomBar = {
            Column {
                PlayerBar(player)
                NavigationBar {
                    TABS.forEachIndexed { i, name ->
                        NavigationBarItem(selected = tab == i, onClick = { tab = i; group = null }, icon = { Icon(TAB_ICONS[i], null) }, label = { Text(name) })
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when {
                !granted -> NoPermission { ask.launch(perm) }
                tab == 3 -> SettingsScreen(prefs)
                group != null -> {
                    val inGroup = shown.filter { (if (tab == 1) it.album else it.artist) == group }
                    SongList(if (tab == 1) inGroup.sortedBy { it.track } else inGroup, player, prefs) { editing = it }
                }
                tab == 0 -> SongList(shown, player, prefs) { editing = it }
                else -> GroupList(if (tab == 1) shown.groupBy { it.album } else shown.groupBy { it.artist }, isAlbum = tab == 1) { group = it }
            }
        }
    }

    editing?.let { s ->
        EditDialog(s, onDismiss = { editing = null }) { t, a, al -> prefs.saveEdit(s.id, t, a, al); editing = null }
    }
}

@Composable
private fun SongList(songs: List<Song>, player: Player, prefs: Prefs, onEdit: (Song) -> Unit) {
    if (songs.isEmpty()) return Center("Nessun brano")
    LazyColumn(Modifier.fillMaxSize()) {
        items(songs, key = { it.id }) { s ->
            val isCurrent = s.id == player.current?.id
            ListItem(
                headlineContent = {
                    Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Unspecified)
                },
                supportingContent = { Text("${s.artist} · ${s.album}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingContent = { AlbumArt(s, 48.dp) },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (prefs.showDuration) Text(fmt(s.duration), style = MaterialTheme.typography.bodySmall)
                        IconButton({ onEdit(s) }) { Icon(Icons.Default.Edit, "Modifica") }
                    }
                },
                modifier = Modifier.clickable { player.play(songs, s) },
            )
        }
    }
}

@Composable
private fun GroupList(groups: Map<String, List<Song>>, isAlbum: Boolean, onOpen: (String) -> Unit) {
    if (groups.isEmpty()) return Center("Nessun elemento")
    LazyColumn(Modifier.fillMaxSize()) {
        items(groups.keys.sortedBy { it.lowercase() }) { name ->
            val list = groups.getValue(name)
            ListItem(
                headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Text(if (isAlbum) "${list[0].artist} · ${brani(list.size)}" else "${list.map { it.album }.distinct().size} album · ${brani(list.size)}")
                },
                leadingContent = { if (isAlbum) AlbumArt(list[0], 48.dp) else Icon(Icons.Default.Person, null, Modifier.size(48.dp)) },
                modifier = Modifier.clickable { onOpen(name) },
            )
        }
    }
}

@Composable
private fun PlayerBar(player: Player) {
    val song = player.current ?: return
    var pos by remember { mutableIntStateOf(0) }
    LaunchedEffect(song, player.playing) { while (true) { pos = player.position; delay(500) } }
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.padding(horizontal = 8.dp)) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AlbumArt(song, 44.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(song.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(player::prev) { Icon(IcPrev, "Precedente") }
                FilledIconButton(player::toggle) { Icon(if (player.playing) IcPause else Icons.Default.PlayArrow, "Play/Pausa") }
                IconButton(player::next) { Icon(IcNext, "Successivo") }
            }
            Slider(
                value = pos.toFloat(),
                onValueChange = { pos = it.toInt(); player.seekTo(it.toInt()) },
                valueRange = 0f..song.duration.toFloat().coerceAtLeast(1f),
            )
        }
    }
}

@Composable
private fun AlbumArt(song: Song, size: Dp) {
    val ctx = LocalContext.current
    // ponytail: nessuna cache in memoria, MediaProvider ha già la sua su disco.
    val bmp by produceState<Bitmap?>(null, song.id) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= 29) ctx.contentResolver.loadThumbnail(song.uri, Size(256, 256), null)
                else ctx.contentResolver.openInputStream(ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), song.albumId))
                    ?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }
    }
    Box(Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(IcMusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsScreen(prefs: Prefs) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Section("Aspetto")
        Choice("Tema", listOf("system" to "Sistema", "light" to "Chiaro", "dark" to "Scuro"), prefs.theme) { prefs.theme = it }
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
        Section("Libreria")
        Choice("Ordina brani per", listOf("title" to "Titolo", "artist" to "Artista", "album" to "Album", "date" to "Più recenti"), prefs.sort) { prefs.sort = it }
        Toggle("Mostra durata", prefs.showDuration) { prefs.showDuration = it }
        Section("Riproduzione")
        Toggle("Riproduzione casuale", prefs.shuffle) { prefs.shuffle = it }
        Toggle("Ripeti la coda", prefs.repeat) { prefs.repeat = it }
        Section("Info")
        Text("Musica 1.1 · Le modifiche ai brani sono salvate nell'app, i file non vengono toccati.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EditDialog(song: Song, onDismiss: () -> Unit, onSave: (String?, String?, String?) -> Unit) {
    var t by remember { mutableStateOf(song.title) }
    var a by remember { mutableStateOf(song.artist) }
    var al by remember { mutableStateOf(song.album) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifica brano") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(t, { t = it }, label = { Text("Titolo") }, singleLine = true)
                OutlinedTextField(a, { a = it }, label = { Text("Artista") }, singleLine = true)
                OutlinedTextField(al, { al = it }, label = { Text("Album") }, singleLine = true)
                TextButton({ onSave(null, null, null) }) { Text("Ripristina originale") }
            }
        },
        confirmButton = { TextButton({ onSave(t.trim(), a.trim(), al.trim()) }, enabled = t.isNotBlank()) { Text("Salva") } },
        dismissButton = { TextButton(onDismiss) { Text("Annulla") } },
    )
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
private fun Center(text: String) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text) }

@Composable
private fun Section(title: String) =
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))

@Composable
private fun Toggle(label: String, on: Boolean, set: (Boolean) -> Unit) =
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(on, set) }

@Composable
private fun Choice(label: String, options: List<Pair<String, String>>, value: String, set: (String) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (k, v) -> FilterChip(selected = value == k, onClick = { set(k) }, label = { Text(v) }) }
    }
}

private fun fmt(ms: Long) = "%d:%02d".format(ms / 60000, ms / 1000 % 60)
private fun brani(n: Int) = if (n == 1) "1 brano" else "$n brani"

// Icone Material non incluse in icons-core, ricostruite dal path (evita la dipendenza icons-extended).
private fun vec(d: String) = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    .addPath(PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.Black)).build()
private val IcPause = vec("M6 19h4V5H6v14zm8-14v14h4V5h-4z")
private val IcNext = vec("M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z")
private val IcPrev = vec("M6 6h2v12H6zm3.5 6l8.5 6V6z")
private val IcMusicNote = vec("M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z")
private val IcAlbum = vec("M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 14.5c-2.49 0-4.5-2.01-4.5-4.5S9.51 7.5 12 7.5s4.5 2.01 4.5 4.5-2.01 4.5-4.5 4.5zm0-5.5c-.55 0-1 .45-1 1s.45 1 1 1 1-.45 1-1-.45-1-1-1z")
private val TAB_ICONS = listOf(IcMusicNote, IcAlbum, Icons.Default.Person, Icons.Default.Settings)
