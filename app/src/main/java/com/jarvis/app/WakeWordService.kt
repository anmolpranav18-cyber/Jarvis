package com.jarvis.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import java.util.Locale

/**
 * Runs continuously in the background (foreground service, so Android
 * won't kill it for using the mic) and listens for the wake word
 * "Jarvis" using the Porcupine engine. When triggered, it captures a
 * spoken command, processes it, and speaks a response — then goes
 * back to listening for the wake word.
 */
class WakeWordService : Service() {

    companion object {
        const val CHANNEL_ID = "jarvis_channel"
        const val NOTIF_ID = 1
        const val ACTION_LOG = "com.jarvis.app.LOG"
        const val EXTRA_LOG = "log"

        // Get a free key at console.picovoice.ai and paste it here,
        // or better: load it from local.properties / BuildConfig so
        // it never gets committed to source control.
        const val PICOVOICE_ACCESS_KEY = "YOUR_PICOVOICE_ACCESS_KEY"
    }

    private var porcupineManager: PorcupineManager? = null
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private val commandProcessor = CommandProcessor()
    private var listeningForCommand = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Listening for \"Jarvis\"…"))
        tts = TextToSpeech(this) { }
        startWakeWordEngine()
    }

    private fun startWakeWordEngine() {
        try {
            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(PICOVOICE_ACCESS_KEY)
                .setKeyword(Porcupine.BuiltInKeyword.JARVIS)
                .build(applicationContext, wakeWordCallback)
            porcupineManager?.start()
            log("Wake word engine started. Say \"Jarvis\".")
        } catch (e: Exception) {
            log("Failed to start wake word engine: ${e.message}. Check your Picovoice access key.")
        }
    }

    private val wakeWordCallback = PorcupineManagerCallback { _ ->
        if (!listeningForCommand) {
            listeningForCommand = true
            log("Wake word heard.")
            speak("Yes?") {
                listenForCommand()
            }
        }
    }

    private fun listenForCommand() {
        // Pause wake-word detection while we capture the actual command,
        // since both would otherwise fight over the microphone.
        porcupineManager?.stop()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: android.os.Bundle?) {
                    val heard = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull() ?: ""
                    log("You said: $heard")
                    val response = commandProcessor.process(heard)
                    log("Jarvis: $response")
                    speak(response) { resumeWakeWordListening() }
                }

                override fun onError(error: Int) {
                    log("Didn't catch a command.")
                    resumeWakeWordListening()
                }

                override fun onReadyForSpeech(params: android.os.Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: android.os.Bundle?) {}
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })
            startListening(intent)
        }
    }

    private fun resumeWakeWordListening() {
        listeningForCommand = false
        speechRecognizer?.destroy()
        speechRecognizer = null
        try {
            porcupineManager?.start()
        } catch (_: Exception) {
        }
        log("Listening for \"Jarvis\"…")
    }

    private fun speak(text: String, onDone: (() -> Unit)? = null) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_utterance")
        if (onDone != null) {
            // Simple delay-based continuation. For production, use
            // TextToSpeech.setOnUtteranceProgressListener instead.
            android.os.Handler(mainLooper).postDelayed(onDone, 900L + text.length * 40L)
        }
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
        porcupineManager?.stop()
        porcupineManager?.delete()
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
