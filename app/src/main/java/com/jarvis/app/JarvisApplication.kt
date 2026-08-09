package com.jarvis.app

import android.app.Application
import java.io.PrintWriter
import java.io.StringWriter

class JarvisApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                getSharedPreferences("jarvis_crash", MODE_PRIVATE)
                    .edit()
                    .putString("last_crash", sw.toString())
                    .apply()
            } catch (_: Throwable) {
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}

