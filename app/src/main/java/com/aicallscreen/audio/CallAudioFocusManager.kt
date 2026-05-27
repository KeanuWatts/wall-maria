package com.aicallscreen.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * Manages in-call audio routing without requesting global transient audio focus.
 *
 * Deliberately avoids [AudioManager.AUDIOFOCUS_GAIN_TRANSIENT] and similar modes that would
 * pause background media players (e.g. Spotify). Communication mode is set locally for the
 * telephony stack while music ducking/pause broadcasts are not triggered.
 */
class CallAudioFocusManager(
    context: Context,
) {

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var previousMode: Int = AudioManager.MODE_NORMAL
    private var previousSpeakerphone: Boolean = false

    private val voiceCommunicationAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    /**
     * Configures the device for a voice call without acquiring exclusive audio focus.
     */
    fun enterCallAudioMode() {
        previousMode = audioManager.mode
        previousSpeakerphone = audioManager.isSpeakerphoneOn

        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // MAY_DUCK is intentionally not requested — we never call requestAudioFocus().
            val unusedFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(voiceCommunicationAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener { /* no-op: we do not hold focus */ }
                .build()
            Log.d(TAG, "Prepared focus request (not submitted): $unusedFocusRequest")
        }
    }

    /**
     * Restores prior audio mode after screening completes.
     */
    fun exitCallAudioMode() {
        audioManager.isSpeakerphoneOn = previousSpeakerphone
        audioManager.mode = previousMode
    }

    fun voiceCommunicationAttributes(): AudioAttributes = voiceCommunicationAttributes

    companion object {
        private const val TAG = "CallAudioFocusManager"
    }
}
