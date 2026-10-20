package fr.geotower.assistant

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * Synthèse vocale locale utilisant le moteur Android TextToSpeech.
 * Ne nécessite aucun appel réseau ni serveur cloud.
 */
class VoiceSpeaker(context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false
    private var pendingText: String? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.FRENCH)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "Langue française non supportée par le moteur TTS par défaut.")
                    tts?.setLanguage(Locale.getDefault())
                }
                isReady = true
                pendingText?.let { text ->
                    speak(text)
                    pendingText = null
                }
            } else {
                Log.e(TAG, "Échec de l'initialisation du moteur TextToSpeech (status=$status)")
            }
        }
    }

    fun speak(text: String) {
        if (isReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        } else {
            pendingText = text
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }

    companion object {
        private const val TAG = "VoiceSpeaker"
        private const val UTTERANCE_ID = "GeoTowerVoiceUtterance"
    }
}
