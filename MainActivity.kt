package com.example.btvolumeknob

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private var started = false

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->

            val ok =
                result[Manifest.permission.BLUETOOTH_CONNECT] == true &&
                        result[Manifest.permission.BLUETOOTH_SCAN] == true

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ok) {
                startServiceOnce()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissions()
    }

    private fun requestPermissions() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            startServiceOnce()
            return
        }

        val missing = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) missing.add(Manifest.permission.BLUETOOTH_CONNECT)

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) missing.add(Manifest.permission.BLUETOOTH_SCAN)

        if (missing.isEmpty()) {
            startServiceOnce()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startServiceOnce() {

        if (started) return
        started = true

        val intent = Intent(this, VolumeService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        // safe finish AFTER service request is queued
        finish()
    }
}