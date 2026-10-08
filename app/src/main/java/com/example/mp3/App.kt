package com.example.mp3

import android.app.Application
import android.util.Log
import java.io.File
import java.util.Date

/** Salva l'ultimo crash in filesDir/crash.log, consultabile da Impostazioni → Diagnostica. */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { File(filesDir, "crash.log").writeText("${Date()}\n${Log.getStackTraceString(e)}") }
            default?.uncaughtException(t, e)
        }
    }
}
