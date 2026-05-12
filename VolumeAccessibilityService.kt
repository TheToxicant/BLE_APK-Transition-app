package com.example.btvolumeknob

import android.accessibilityservice.AccessibilityService
import android.media.AudioManager
import android.view.accessibility.AccessibilityEvent

class VolumeAccessibilityService : AccessibilityService() {

    companion object {
        var instance: VolumeAccessibilityService? = null
    }

    private lateinit var audioManager: AudioManager

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        android.util.Log.d("ACCESS_VOLUME", "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    fun volumeUp() {
        android.util.Log.d("ACCESS_VOLUME", "volumeUp accessibility adjust")
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_RAISE,
            AudioManager.FLAG_SHOW_UI or AudioManager.FLAG_PLAY_SOUND
        )
    }

    fun volumeDown() {
        android.util.Log.d("ACCESS_VOLUME", "volumeDown accessibility adjust")
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI or AudioManager.FLAG_PLAY_SOUND
        )
    }
}