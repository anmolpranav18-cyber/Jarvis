package com.jarvis.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.util.Locale

class WakeWordService : Service() {

    companion object {
        const val CHANNEL_ID = "jarvis_channel"
        const val NOTIF_ID = 1
        const val ACTION_LOG = "com.jarvis.app.LOG"
        const val EXTRA_LOG = "log"
        const val WAKE_WORD = "jarvis"
    }

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private val commandProcessor = CommandProcessor()
    private var awaitingCommand = false
    private var running = false
    private val handler by lazy { Handler(mainLooper) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Listening for \"Jarvis\"…"))
        tts = TextToSpeech(this) { }
        running = true
        startListeningCycle()
    }

    private fun startListeningCycle() {
        if (!running) return

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            log("Speech recognition not available on this device.")
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val heard = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.lowercase(Locale.getDefault()) ?: ""

                    handleHeard(heard)
                    handler.postDelayed({ startListeningCycle() }, 400)
                }

                override fun onError(error: Int) {
                    handler.postDelayed({ startListeningCycle() }, 400)
                }

                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            startListening(intent)
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

        // Nothing matched locally — try a free Wikipedia lookup as a
        // last resort before giving up.
        log("Looking that up…")
        Thread {
            val topic = text.removePrefix("what is ").removePrefix("who is ")
                .removePrefix("what's ").removePrefix("tell me about ").trim()
            val answer = WikipediaClient.lookup(topic.ifBlank { text })
                ?: "I heard: $text. I couldn't find anything on that."
            handler.post {
                log("Jarvis: $answer")
                speak(answer)
            }
        }.start()
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_utterance")
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
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

