package com.example.mp3

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

class MainActivity : Activity() {
    private val player = MediaPlayer()
    private val songs = mutableListOf<Pair<String, Uri>>()
    private var index = -1
    private lateinit var title: TextView
    private lateinit var playPause: Button
    private lateinit var list: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = TextView(this).apply { textSize = 18f; setPadding(32, 32, 32, 16); text = "Nessun brano" }
        playPause = button("▶") { toggle() }
        list = ListView(this).apply { setOnItemClickListener { _, _, i, _ -> play(i) } }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            addView(title)
            addView(LinearLayout(context).apply {
                addView(button("⏮") { play(index - 1) })
                addView(playPause)
                addView(button("⏭") { play(index + 1) })
            })
            addView(list, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        })
        player.setOnCompletionListener { play(index + 1) }

        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
                   else Manifest.permission.READ_EXTERNAL_STORAGE
        if (checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED) load()
        else requestPermissions(arrayOf(perm), 0)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) load()
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        setOnClickListener { onClick() }
    }

    private fun load() {
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        contentResolver.query(
            uri,
            arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST),
            "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                songs += "${c.getString(1)} — ${c.getString(2)}" to ContentUris.withAppendedId(uri, c.getLong(0))
            }
        }
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, songs.map { it.first })
        if (songs.isEmpty()) title.text = "Nessun brano trovato"
    }

    private fun play(i: Int) {
        if (songs.isEmpty()) return
        index = i.mod(songs.size)
        player.reset()
        player.setDataSource(this, songs[index].second)
        player.prepare()
        player.start()
        title.text = songs[index].first
        playPause.text = "⏸"
    }

    private fun toggle() {
        if (index < 0) return play(0)
        if (player.isPlaying) player.pause() else player.start()
        playPause.text = if (player.isPlaying) "⏸" else "▶"
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}
