package com.jarvis.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract

/**
 * Handles phone-level actions: calling, texting, alarms, timers,
 * web search, and simple math. All use standard Android intents that
 * don't require risky runtime permissions — calling and texting open
 * the dialer/messaging app pre-filled rather than sending directly,
 * so you always confirm before anything actually goes out.
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

