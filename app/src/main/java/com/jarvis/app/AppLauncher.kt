package com.jarvis.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Handles "open <app name>" voice commands by finding an installed app
 * whose name matches and launching it.
 */
object AppLauncher {
    fun tryOpenApp(context: Context, heard: String): Boolean {
        val text = heard.lowercase().trim()
        if (!text.startsWith("open ")) return false

        val name = text.removePrefix("open ").trim()
        if (name.isEmpty()) return false

        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        val match = apps.firstOrNull {
            val label = pm.getApplicationLabel(it).toString().lowercase()
            label.isNotBlank() && (label.contains(name) || name.contains(label))
        } ?: return false

        val intent = pm.getLaunchIntentForPackage(match.packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }
}

