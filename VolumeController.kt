package com.example.btvolumeknob

import android.content.Context
import android.media.AudioManager

class VolumeController(private val context: Context) {

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val stepSize = 1

    fun volumeUp() {
        changeVolume(stepSize)
    }

    fun volumeDown() {
        changeVolume(-stepSize)
    }

    private fun changeVolume(delta: Int) {
        val stream = AudioManager.STREAM_MUSIC

        val current = audioManager.getStreamVolume(stream)
        val max = audioManager.getStreamMaxVolume(stream)
        val newVolume = (current + delta).coerceIn(0, max)

        android.util.Log.d("VOLUME", "current=$current new=$newVolume max=$max")

        audioManager.setStreamVolume(
            stream,
            newVolume,
            AudioManager.FLAG_SHOW_UI or AudioManager.FLAG_PLAY_SOUND
        )
    }
}