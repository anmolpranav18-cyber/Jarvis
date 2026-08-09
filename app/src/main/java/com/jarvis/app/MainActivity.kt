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
import android.widget.ScrollView
import android.widget.TextView
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
        try {
            setContentView(R.layout.activity_main)

            statusText = findViewById(R.id.statusText)
            logText = findViewById(R.id.logText)
            toggleButton = findViewById(R.id.toggleButton)

            toggleButton.setOnClickListener {
                if (serviceRunning) stopJarvis() else requestPermissionsAndStart()
            }
        } catch (e: Throwable) {
            showCrash(e)
        }
    }

    private fun showCrash(e: Throwable) {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        val scroll = ScrollView(this)
        val tv = TextView(this).apply {
            text = "Startup error:\n\n${sw}"
            setTextIsSelectable(true)
            setPadding(32, 32, 32, 32)
            textSize = 12f
        }
        scroll.addView(tv)
        setContentView(scroll)
    }

    private fun requestPermissionsAndStart() {
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
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
        if (::statusText.isInitialized) {
            LocalBroadcastManager.getInstance(this)
                .registerReceiver(logReceiver, IntentFilter(WakeWordService.ACTION_LOG))
        }
    }

    override fun onStop() {
        if (::statusText.isInitialized) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(logReceiver)
        }
        super.onStop()
    }
}
