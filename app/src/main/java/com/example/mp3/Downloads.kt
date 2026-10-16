package com.example.mp3

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.ZipInputStream

// ---- Internet Archive (archive.org): API pubblica, senza chiave ----

data class IaItem(val id: String, val title: String, val creator: String, val year: String)
data class IaFile(val name: String, val size: Long) { val isZip get() = name.endsWith(".zip", ignoreCase = true) }

private val AUDIO_EXT = setOf("mp3", "flac", "ogg", "opus", "m4a", "wav", "aac")
fun isAudioName(n: String) = n.substringAfterLast('.', "").lowercase() in AUDIO_EXT

private fun get(url: String): String {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 15_000
    c.readTimeout = 30_000
    c.setRequestProperty("User-Agent", "Musica-Android")
    if (c.responseCode != 200) error("HTTP ${c.responseCode}")
    return c.inputStream.bufferedReader().readText()
}

// Alcuni campi arrivano come stringa, altri come lista: prendiamo il primo valore.
private fun str(o: JSONObject, k: String): String = when (val v = o.opt(k)) { null -> ""; is JSONArray -> v.optString(0); else -> v.toString() }

fun iaSearch(query: String): List<IaItem> {
    val q = query.replace(Regex("""[():\[\]"]"""), " ").trim()
    fun run(expr: String) = parseSearch(get(
        "https://archive.org/advancedsearch.php?q=" + URLEncoder.encode("($expr) AND mediatype:audio", "UTF-8") +
            "&fl[]=identifier&fl[]=title&fl[]=creator&fl[]=year&sort[]=downloads+desc&rows=50&output=json"
    ))
    // Prima titolo e autore (preciso, i più scaricati in alto); testo libero (descrizioni, podcast…) solo se non trova nulla.
    return run("title:($q) OR creator:($q)").ifEmpty { run(q) }
}

internal fun parseSearch(json: String): List<IaItem> {
    val docs = JSONObject(json).getJSONObject("response").getJSONArray("docs")
    return List(docs.length()) { i -> val d = docs.getJSONObject(i); IaItem(d.getString("identifier"), str(d, "title"), str(d, "creator"), str(d, "year")) }
}

fun iaFiles(id: String): List<IaFile> = parseFiles(get("https://archive.org/metadata/$id"))

internal fun parseFiles(json: String): List<IaFile> {
    val files = JSONObject(json).optJSONArray("files") ?: return emptyList()
    return List(files.length()) { files.getJSONObject(it) }
        .filter { it.optString("source") != "derivative" } // solo i file caricati, non le versioni generate da archive.org (spettrogrammi, zip di immagini…)
        .map { IaFile(it.getString("name"), it.optString("size").toLongOrNull() ?: 0) }
        .filter { isAudioName(it.name) || it.isZip }
        .sortedBy { it.name.lowercase() }
}

fun iaUrl(id: String, name: String) =
    "https://archive.org/download/$id/" + name.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

/** Nome valido per una cartella o un file in Music/. */
fun cleanName(s: String) = s.replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), " ").replace(Regex("\\s+"), " ").trim().take(60).ifBlank { "Senza nome" }

fun fmtSize(b: Long) = when {
    b >= 1_000_000_000 -> "%.1f GB".format(b / 1e9)
    b >= 1_000_000 -> "%.0f MB".format(b / 1e6)
    else -> "%.0f KB".format(b / 1e3)
}

// ---- Coda di download, osservata dalla UI ----

/** Un file da scaricare in Music/[folder]; gli zip vengono estratti al volo (solo i file audio dentro). */
data class Job(val url: String, val folder: String, val name: String, val size: Long)

object Downloads {
    var queue: List<Job> by mutableStateOf(emptyList()); private set
    var current: Job? by mutableStateOf(null); internal set
    var progress by mutableFloatStateOf(0f); internal set
    /** Ultimi esiti, il più recente per primo. */
    var log: List<String> by mutableStateOf(emptyList()); private set
    @Volatile internal var cancelled = false

    @Synchronized fun enqueue(ctx: Context, jobs: List<Job>) {
        if (Build.VERSION.SDK_INT < 29) { note("Serve Android 10 o successivo"); return }
        queue = queue + jobs
        ctx.startForegroundService(Intent(ctx, DownloadService::class.java))
    }
    fun cancel() { synchronized(this) { queue = emptyList() }; cancelled = true }
    @Synchronized internal fun next(): Job? = queue.firstOrNull()?.also { queue = queue.drop(1) }
    internal fun note(s: String) { log = (listOf(s) + log).take(5) }
}

/** Servizio in foreground (notifica con avanzamento): i download continuano anche con l'app chiusa. */
class DownloadService : Service() {
    private val nm by lazy { getSystemService(NotificationManager::class.java) }
    private var worker: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) { Downloads.cancel(); return START_NOT_STICKY }
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CH, "Download", NotificationManager.IMPORTANCE_LOW))
        val n = notification("In attesa…", 0)
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(ID, n)
        if (worker?.isAlive != true) worker = Thread {
            cleanupPending()
            var ok = 0
            while (true) {
                val job = Downloads.next() ?: break
                Downloads.current = job; Downloads.progress = 0f; Downloads.cancelled = false
                runCatching { download(job) }
                    .onSuccess { ok++; Downloads.note("✓ ${job.name}") }
                    .onFailure { Downloads.note(if (Downloads.cancelled) "Annullato: ${job.name}" else "✗ ${job.name}: ${it.message}") }
            }
            Downloads.current = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            if (ok > 0) nm.notify(ID + 1, notification(if (ok == 1) "1 file scaricato" else "$ok file scaricati", -1))
            stopSelf()
        }.also { it.start() }
        return START_NOT_STICKY
    }

    private fun download(job: Job) {
        val c = URL(job.url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 60_000
        c.setRequestProperty("User-Agent", "Musica-Android")
        if (c.responseCode != 200) error("HTTP ${c.responseCode}")
        val total = if (c.contentLengthLong > 0) c.contentLengthLong else job.size
        var done = 0L
        var lastNotif = 0L
        val counted = object : FilterInputStream(c.inputStream) { // avanzamento sui byte scaricati, annullamento
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (Downloads.cancelled) throw InterruptedException("annullato")
                val n = super.read(b, off, len)
                if (n > 0 && total > 0) { done += n; Downloads.progress = done.toFloat() / total }
                val now = System.currentTimeMillis()
                if (now - lastNotif > 1000) {
                    lastNotif = now
                    val rest = Downloads.queue.size
                    nm.notify(ID, notification(job.name + if (rest > 0) " · $rest in coda" else "", (Downloads.progress * 100).toInt()))
                }
                return n
            }
            override fun read(): Int = ByteArray(1).let { if (read(it, 0, 1) == 1) it[0].toInt() and 0xFF else -1 }
        }
        counted.use { s ->
            if (job.name.endsWith(".zip", ignoreCase = true)) {
                // ISO-8859-1: i nomi non UTF-8 (zip fatti su Windows) vengono letti comunque invece di far saltare l'estrazione.
                val z = ZipInputStream(s, Charsets.ISO_8859_1)
                var any = false
                while (true) {
                    val e = z.nextEntry ?: break
                    val n = e.name.substringAfterLast('/')
                    if (e.isDirectory || n.startsWith("._") || !isAudioName(n)) continue
                    save(job.folder, n, z)
                    any = true
                }
                if (!any) error("nessun file audio nello zip")
            } else save(job.folder, job.name, s)
        }
    }

    /** Scrive Music/[folder]/[name] tramite MediaStore (visibile a tutte le app); il file resta nascosto finché non è completo. */
    private fun save(folder: String, name: String, input: InputStream) {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/$folder")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: error("MediaStore ha rifiutato $name")
        try {
            contentResolver.openOutputStream(uri)!!.use { out -> input.copyTo(out, 256 * 1024) }
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
        } catch (e: Exception) {
            contentResolver.delete(uri, null, null)
            throw e
        }
    }

    /** File rimasti a metà da un download interrotto (ancora nascosti): via. */
    @Suppress("DEPRECATION")
    private fun cleanupPending() {
        if (Build.VERSION.SDK_INT < 29) return
        runCatching {
            contentResolver.delete(
                MediaStore.setIncludePending(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI),
                "${MediaStore.Audio.Media.IS_PENDING}=1 AND ${MediaStore.Audio.Media.OWNER_PACKAGE_NAME}=?", arrayOf(packageName),
            )
        }
    }

    private fun notification(text: String, pct: Int): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val cancel = PendingIntent.getService(this, 1, Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL), PendingIntent.FLAG_IMMUTABLE)
        @Suppress("DEPRECATION")
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CH) else Notification.Builder(this)
        return b.setSmallIcon(if (pct >= 0) android.R.drawable.stat_sys_download else android.R.drawable.stat_sys_download_done)
            .setContentTitle("Musica · download").setContentText(text).setContentIntent(open).setOnlyAlertOnce(true)
            .apply {
                if (pct >= 0) {
                    setProgress(100, pct, pct == 0)
                    addAction(Notification.Action.Builder(null, "Annulla", cancel).build())
                }
            }
            .build()
    }

    companion object {
        private const val CH = "download"
        private const val ID = 2
        private const val ACTION_CANCEL = "com.example.mp3.DL_CANCEL"
    }
}
