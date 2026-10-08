package com.example.mp3

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

const val APK_URL = "https://github.com/MikiSamba/Mp3/releases/download/latest/Mp3.apk"

/** Esito del controllo: numero di build pubblicato (campo "build=N" nelle note della release "latest") oppure un errore leggibile. */
class UpdateCheck(val build: Int?, val error: String?)

fun fetchLatestBuild(): UpdateCheck = try {
    val c = URL("https://api.github.com/repos/MikiSamba/Mp3/releases/tags/latest").openConnection() as HttpURLConnection
    c.connectTimeout = 8_000
    c.readTimeout = 8_000
    c.setRequestProperty("Accept", "application/vnd.github+json")
    c.setRequestProperty("User-Agent", "Musica-Android")
    val code = c.responseCode
    if (code != 200) UpdateCheck(null, "GitHub ha risposto $code")
    else {
        val body = JSONObject(c.inputStream.bufferedReader().readText()).optString("body")
        val b = Regex("""build=(\d+)""").find(body)?.groupValues?.get(1)?.toInt()
        UpdateCheck(b, if (b == null) "Numero di build assente nella release" else null)
    }
} catch (e: Exception) {
    UpdateCheck(null, e.toString())
}
