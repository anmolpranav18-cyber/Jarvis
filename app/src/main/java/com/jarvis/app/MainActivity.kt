package com.jarvis.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.PrintWriter
import java.io.StringWriter

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var toggleButton: Button
    private var serviceRunning = false
    private var uiReady = false

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val line = intent?.getStringExtra(WakeWordService.EXTRA_LOG) ?: return
            statusText.text = line
            logText.append("$line\n")
        }
    }

    private val permissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted[Manifest.permission.RECORD_AUDIO] == true) {
            startJarvis()
        } else {
            statusText.text = "Microphone permission is required."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val crashPrefs = getSharedPreferences("jarvis_crash", MODE_PRIVATE)
        val lastCrash = crashPrefs.getString("last_crash", null)
        if (lastCrash != null) {
            crashPrefs.edit().remove("last_crash").apply()
            showCrashText(lastCrash)
            return
        }

        try {
            setContentView(R.layout.activity_main)
            statusText = findViewById(R.id.statusText)
            logText = findViewById(R.id.logText)
            toggleButton = findViewById(R.id.toggleButton)
            uiReady = true

            toggleButton.setOnClickListener {
                if (serviceRunning) stopJarvis() else requestPermissionsAndStart()
            }

            val apiKeyInput = findViewById<EditText>(R.id.apiKeyInput)
            val saveKeyButton = findViewById<Button>(R.id.saveKeyButton)
            val prefs = getSharedPreferences("jarvis", MODE_PRIVATE)
            apiKeyInput.setText(prefs.getString("groq_api_key", ""))
            saveKeyButton.setOnClickListener {
                prefs.edit().putString("groq_api_key", apiKeyInput.text.toString().trim()).apply()
                Toast.makeText(this, "API key saved", Toast.LENGTH_SHORT).show()
            }

            val overlayPermButton = findViewById<Button>(R.id.overlayPermButton)
            overlayPermButton.setOnClickListener {
                if (!android.provider.Settings.canDrawOverlays(this)) {
                    val overlayIntent = Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:$packageName")
                    )
                    startActivity(overlayIntent)
                } else {
                    Toast.makeText(this, "Already enabled", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Throwable) {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            showCrashText(sw.toString())
        }
    }

    private fun showCrashText(text: String) {
        val scroll = ScrollView(this)
        val tv = TextView(this).apply {
            setText("Startup error:\n\n$text")
            setTextIsSelectable(true)
            setPadding(32, 32, 32, 32)
            textSize = 12f
        }
        scroll.addView(tv)
        setContentView(scroll)
    }

    private fun requestPermissionsAndStart() {
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            startJarvis()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startJarvis() {
        try {
            ContextCompat.startForegroundService(this, Intent(this, WakeWordService::class.java))
            getSharedPreferences("jarvis", MODE_PRIVATE).edit().putBoolean("enabled", true).apply()
            serviceRunning = true
            toggleButton.text = "Deactivate Jarvis"
            statusText.text = "Starting…"
        } catch (e: Throwable) {
            statusText.text = "Failed to start: ${e.message}"
        }
    }

    private fun stopJarvis() {
        stopService(Intent(this, WakeWordService::class.java))
        getSharedPreferences("jarvis", MODE_PRIVATE).edit().putBoolean("enabled", false).apply()
        serviceRunning = false
        toggleButton.text = "Activate Jarvis"
        statusText.text = "Standing by"
    }

    override fun onStart() {
        super.onStart()
        if (uiReady) {
            LocalBroadcastManager.getInstance(this)
                .registerReceiver(logReceiver, IntentFilter(WakeWordService.ACTION_LOG))
        }
    }

    override fun onStop() {
        if (uiReady) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(logReceiver)
        }
        super.onStop()
    }
}

