package com.jarvis.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.util.Locale

/**
 * Runs continuously in the background (foreground service, so Android
 * won't kill it for using the mic) and listens for the word "Jarvis"
 * using Vosk — a fully offline, free speech engine with no account or
 * API key, and no external "listening" sound since it never invokes
 * Android's built-in recognizer UI.
 */
class WakeWordService : Service(), RecognitionListener {

    companion object {
        const val CHANNEL_ID = "jarvis_channel"
        const val NOTIF_ID = 1
        const val ACTION_LOG = "com.jarvis.app.LOG"
        const val EXTRA_LOG = "log"
        const val WAKE_WORD = "jarvis"
        const val SAMPLE_RATE = 16000.0f
    }

    private var tts: android.speech.tts.TextToSpeech? = null
    private var model: Model? = null
    private var speechService: SpeechService? = null
    private val commandProcessor = CommandProcessor()
    private var awaitingCommand = false
    private var running = false
    private val handler by lazy { Handler(mainLooper) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Loading voice model…"))
        tts = android.speech.tts.TextToSpeech(this) { }
        running = true
        log("Loading voice model…")
        StorageService.unpack(
            this, "model-en-us", "model",
            { loadedModel ->
                model = loadedModel
                startListening()
                log("Listening for \"Jarvis\"…")
            },
            { exception ->
                log("Failed to load voice model: ${exception.message}")
            }
        )
    }

    private fun startListening() {
        if (!running) return
        val m = model ?: return
        try {
            val recognizer = Recognizer(m, SAMPLE_RATE)
            speechService = SpeechService(recognizer, SAMPLE_RATE)
            speechService?.startListening(this)
        } catch (e: Exception) {
            log("Failed to start listening: ${e.message}")
        }
    }

    override fun onResult(hypothesis: String?) {
        val text = parseText(hypothesis)
        if (text.isNotEmpty()) handleHeard(text)
    }

    override fun onFinalResult(hypothesis: String?) {
        val text = parseText(hypothesis)
        if (text.isNotEmpty()) handleHeard(text)
    }

    override fun onPartialResult(hypothesis: String?) {
        // Not used — we act on completed phrases only.
    }

    override fun onError(exception: Exception?) {
        log("Recognition error, restarting…")
        handler.postDelayed({ startListening() }, 500)
    }

    override fun onTimeout() {
        speechService?.stop()
        handler.postDelayed({ startListening() }, 200)
    }

    private fun parseText(hypothesis: String?): String {
        if (hypothesis.isNullOrBlank()) return ""
        return try {
            JSONObject(hypothesis).optString("text", "").lowercase(Locale.getDefault())
        } catch (e: Exception) {
            ""
        }
    }

    private fun handleHeard(heard: String) {
        if (heard.isEmpty()) return

        if (awaitingCommand) {
            awaitingCommand = false
            log("You said: $heard")
            respondTo(heard)
            return
        }

        if (heard.contains(WAKE_WORD)) {
            log("Wake word heard.")
            val after = heard.substringAfter(WAKE_WORD).trim()
            if (after.isNotEmpty()) {
                log("You said: $after")
                respondTo(after)
            } else {
                awaitingCommand = true
                speak("Yes?")
            }
        }
    }

    private fun respondTo(text: String) {
        if (AppLauncher.tryOpenApp(this, text)) {
            log("Jarvis: Opening it now.")
            speak("Opening it now.")
            return
        }
        if (SystemActions.tryCall(this, text)) {
            log("Jarvis: Calling.")
            speak("Calling.")
            return
        }
        if (SystemActions.tryText(this, text)) {
            log("Jarvis: Opening your message.")
            speak("Opening your message.")
            return
        }
        if (SystemActions.tryAlarm(this, text)) {
            log("Jarvis: Alarm set.")
            speak("Alarm set.")
            return
        }
        if (SystemActions.tryTimer(this, text)) {
            log("Jarvis: Timer started.")
            speak("Timer started.")
            return
        }
        if (SystemActions.trySearch(this, text)) {
            log("Jarvis: Here's what I found.")
            speak("Here's what I found.")
            return
        }
        if (SystemActions.tryPhoto(this, text)) {
            log("Jarvis: Opening camera.")
            speak("Opening camera.")
            return
        }
        if (SystemActions.trySettings(this, text)) {
            log("Jarvis: Opening settings.")
            speak("Opening settings.")
            return
        }
        val flashlightResult = SystemActions.tryFlashlight(this, text)
        if (flashlightResult != null) {
            log("Jarvis: $flashlightResult")
            speak(flashlightResult)
            return
        }
        val volumeResult = SystemActions.tryVolume(this, text)
        if (volumeResult != null) {
            log("Jarvis: $volumeResult")
            speak(volumeResult)
            return
        }
        val deviceInfoResult = SystemActions.tryDeviceInfo(this, text)
        if (deviceInfoResult != null) {
            log("Jarvis: $deviceInfoResult")
            speak(deviceInfoResult)
            return
        }
        val mathAnswer = SystemActions.tryMath(text)
        if (mathAnswer != null) {
            log("Jarvis: $mathAnswer")
            speak(mathAnswer)
            return
        }

        val local = commandProcessor.process(text)
        if (!local.startsWith("I heard:")) {
            log("Jarvis: $local")
            speak(local)
            return
        }

        log("Looking that up…")
        Thread {
            val topic = text.removePrefix("what is ").removePrefix("who is ")
                .removePrefix("what's ").removePrefix("tell me about ").trim()
            val wikiAnswer = WikipediaClient.lookup(topic.ifBlank { text })

            if (wikiAnswer != null) {
                handler.post {
                    log("Jarvis: $wikiAnswer")
                    speak(wikiAnswer)
                }
                return@Thread
            }

            val apiKey = getSharedPreferences("jarvis", MODE_PRIVATE).getString("groq_api_key", null)
            val finalAnswer = if (!apiKey.isNullOrBlank()) {
                try {
                    AiClient.ask(apiKey, text)
                } catch (e: Exception) {
                    "Sorry, I couldn't reach the AI service."
                }
            } else {
                "I heard: $text. I don't have an answer for that."
            }
            handler.post {
                log("Jarvis: $finalAnswer")
                speak(finalAnswer)
            }
        }.start()
    }

    private fun speak(text: String) {
        tts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "jarvis_utterance")
    }

    private fun log(message: String) {
        val intent = Intent(ACTION_LOG).putExtra(EXTRA_LOG, message)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Jarvis", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Jarvis")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()

    override fun onDestroy() {
        running = false
        speechService?.stop()
        speechService?.shutdown()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

