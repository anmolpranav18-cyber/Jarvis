package com.jarvis.app

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.StatFs
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings

/**
 * Handles phone-level actions: calling, texting, alarms, timers, web
 * search, math, flashlight, volume, camera, settings shortcuts, and
 * battery/storage info. All use standard Android intents or public
 * system APIs — nothing silent, nothing that bypasses a permission
 * or confirmation screen the phone would normally show.
 */
object SystemActions {

    fun tryCall(context: Context, heard: String): Boolean {
        val text = heard.lowercase().trim()
        if (!text.startsWith("call ")) return false
        val target = text.removePrefix("call ").trim()
        if (target.isEmpty()) return false

        val number = if (target.all { it.isDigit() || it == '+' || it == ' ' }) {
            target
        } else {
            lookupContactNumber(context, target) ?: return false
        }

        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    fun tryText(context: Context, heard: String): Boolean {
        val text = heard.lowercase().trim()
        if (!text.startsWith("text ") && !text.startsWith("message ")) return false

        val body = text.removePrefix("text ").removePrefix("message ").trim()
        val sayIndex = body.indexOf(" saying ")
        val name: String
        val message: String
        if (sayIndex >= 0) {
            name = body.substring(0, sayIndex).trim()
            message = body.substring(sayIndex + 8).trim()
        } else {
            name = body
            message = ""
        }
        if (name.isEmpty()) return false

        val number = if (name.all { it.isDigit() || it == '+' || it == ' ' }) {
            name
        } else {
            lookupContactNumber(context, name) ?: return false
        }

        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number"))
        intent.putExtra("sms_body", message)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    fun tryAlarm(context: Context, heard: String): Boolean {
        val text = heard.lowercase().trim()
        if (!text.contains("alarm")) return false

        val match = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").find(text) ?: return false
        var hour = match.groupValues[1].toIntOrNull() ?: return false
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        val ampm = match.groupValues[3]
        if (ampm == "pm" && hour < 12) hour += 12
        if (ampm == "am" && hour == 12) hour = 0

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return true
    }

    fun tryTimer(context: Context, heard: String): Boolean {
        val text = heard.lowercase().trim()
        if (!text.contains("timer")) return false

        val match = Regex("(\\d+)\\s*(second|minute|hour)").find(text) ?: return false
        val amount = match.groupValues[1].toIntOrNull() ?: return false
        val unit = match.groupValues[2]
        val seconds = when {
            unit.startsWith("hour") -> amount * 3600
            unit.startsWith("minute") -> amount * 60
            else -> amount
        }

        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return true
    }

    fun trySearch(context: Context, heard: String): Boolean {
        val text = heard.lowercase().trim()
        val query = when {
            text.startsWith("search for ") -> text.removePrefix("search for ")
            text.startsWith("search ") -> text.removePrefix("search ")
            text.startsWith("google ") -> text.removePrefix("google ")
            else -> return false
        }.trim()
        if (query.isEmpty()) return false

        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    fun tryMath(heard: String): String? {
        val text = heard.lowercase().trim()
        val match = Regex(
            "([-+]?\\d+(?:\\.\\d+)?)\\s*(plus|minus|times|multiplied by|divided by|x|\\*|/|\\+|-)\\s*([-+]?\\d+(?:\\.\\d+)?)"
        ).find(text) ?: return null

        val a = match.groupValues[1].toDoubleOrNull() ?: return null
        val op = match.groupValues[2]
        val b = match.groupValues[3].toDoubleOrNull() ?: return null

        val result = when (op) {
            "plus", "+" -> a + b
            "minus", "-" -> a - b
            "times", "multiplied by", "x", "*" -> a * b
            "divided by", "/" -> if (b != 0.0) a / b else return "You can't divide by zero."
            else -> return null
        }

        val formatted = if (result == result.toLong().toDouble()) {
            result.toLong().toString()
        } else {
            result.toString()
        }
        return "That's $formatted."
    }

    // --- Flashlight ---
    fun tryFlashlight(context: Context, heard: String): String? {
        val text = heard.lowercase().trim()
        val turnOn = text.contains("flashlight") && (text.contains("on") || text.contains("turn on"))
        val turnOff = text.contains("flashlight") && text.contains("off")
        if (!turnOn && !turnOff) return null

        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return "This device doesn't have a flashlight."
            cameraManager.setTorchMode(cameraId, turnOn)
            if (turnOn) "Flashlight on." else "Flashlight off."
        } catch (e: Exception) {
            "I couldn't control the flashlight."
        }
    }

    // --- Volume ---
    fun tryVolume(context: Context, heard: String): String? {
        val text = heard.lowercase().trim()
        if (!text.contains("volume")) return null
        val up = text.contains("up") || text.contains("increase") || text.contains("raise")
        val down = text.contains("down") || text.contains("decrease") || text.contains("lower")
        if (!up && !down) return null

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val direction = if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        return if (up) "Volume up." else "Volume down."
    }

    // --- Camera ---
    fun tryPhoto(context: Context, heard: String): Boolean {
        val text = heard.lowercase().trim()
        if (!text.contains("take a photo") && !text.contains("take a picture") && text != "open camera") return false
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    // --- Settings shortcuts ---
    fun trySettings(context: Context, heard: String): Boolean {
        val text = heard.lowercase().trim()
        val action = when {
            text.contains("wifi") && text.contains("settings") -> Settings.ACTION_WIFI_SETTINGS
            text.contains("wi-fi") && text.contains("settings") -> Settings.ACTION_WIFI_SETTINGS
            text.contains("bluetooth") && text.contains("settings") -> Settings.ACTION_BLUETOOTH_SETTINGS
            text == "open settings" || text == "settings" -> Settings.ACTION_SETTINGS
            else -> return false
        }
        val intent = Intent(action)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    // --- Battery / storage info ---
    fun tryDeviceInfo(context: Context, heard: String): String? {
        val text = heard.lowercase().trim()

        if (text.contains("battery")) {
            val batteryStatus = context.registerReceiver(
                null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val level = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) {
                val percent = (level * 100 / scale)
                return "Battery is at $percent percent."
            }
            return "I couldn't read the battery level."
        }

        if (text.contains("storage") || text.contains("free space")) {
            return try {
                val stat = StatFs(android.os.Environment.getDataDirectory().path)
                val freeGb = stat.availableBytes / (1024.0 * 1024.0 * 1024.0)
                val totalGb = stat.totalBytes / (1024.0 * 1024.0 * 1024.0)
                "You have %.1f gigabytes free out of %.1f total.".format(freeGb, totalGb)
            } catch (e: Exception) {
                "I couldn't read storage info."
            }
        }

        return null
    }

    private fun lookupContactNumber(context: Context, name: String): String? {
        return try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val contactName = cursor.getString(nameIdx)?.lowercase() ?: continue
                    if (contactName.contains(name) || name.contains(contactName)) {
                        return cursor.getString(numberIdx)
                    }
                }
                null
            }
        } catch (e: SecurityException) {
            null
        }
    }
}

