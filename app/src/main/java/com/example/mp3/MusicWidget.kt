package com.example.mp3

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews

/** Widget 4×1: copertina, titolo, artista, precedente/play/successivo. Aggiornato dal servizio a ogni cambio di stato. */
class MusicWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = render(ctx, null, false, null)

    companion object {
        fun render(ctx: Context, song: Song?, playing: Boolean, art: Bitmap?) {
            val mgr = AppWidgetManager.getInstance(ctx) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, MusicWidget::class.java))
            if (ids.isEmpty()) return
            val v = RemoteViews(ctx.packageName, R.layout.widget)
            v.setTextViewText(R.id.title, song?.title ?: "Musica")
            v.setTextViewText(R.id.artist, song?.artist ?: "Tocca per aprire")
            if (art != null) v.setImageViewBitmap(R.id.art, Bitmap.createScaledBitmap(art, 160, 160, true))
            else v.setImageViewResource(R.id.art, R.drawable.ic_note)
            v.setImageViewResource(R.id.play, if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
            fun svc(action: String) = PendingIntent.getService(
                ctx, action.hashCode(), Intent(ctx, PlaybackService::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            v.setOnClickPendingIntent(R.id.prev, svc(PlaybackService.ACTION_PREV))
            v.setOnClickPendingIntent(R.id.play, svc(PlaybackService.ACTION_TOGGLE))
            v.setOnClickPendingIntent(R.id.next, svc(PlaybackService.ACTION_NEXT))
            v.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java).putExtra(EXTRA_NOW_PLAYING, true),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ))
            mgr.updateAppWidget(ids, v)
        }
    }
}
