package com.shopmanager.chat.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

enum class VoiceLang(val label: String, val tag: String) {
    ENGLISH("English", "en-IN"),
    HINDI("Hindi", "hi-IN"),
    BENGALI("Bengali", "bn-IN");

    companion object {
        fun from(name: String) = values().firstOrNull { it.name == name } ?: ENGLISH
    }
}

class VoiceInput(
    private val ctx: Context,
    private val onText: (text: String, final: Boolean) -> Unit,
    private val onListening: (Boolean) -> Unit,
    private val onError: (String) -> Unit
) {
    private var rec: SpeechRecognizer? = null

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(ctx)

    fun start(languageTag: String) {
        destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        rec = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { onListening(true) }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partialResults: Bundle?) {
                first(partialResults)?.let { onText(it, false) }
            }
            override fun onResults(results: Bundle?) {
                onListening(false)
                first(results)?.let { onText(it, true) }
            }
            override fun onError(error: Int) {
                onListening(false)
                this@VoiceInput.onError(
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                            "Didn't catch that. Tap the mic and try again."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "Voice recognition needs an internet connection for this language."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                            "Microphone permission is needed for voice input."
                        12, 13 -> "This language isn't available for voice input on this device."
                        else -> "Voice input failed (code $error)."
                    }
                )
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        r.startListening(i)
        onListening(true)
    }

    /** Stop listening and deliver the final result. */
    fun stop() { rec?.stopListening() }

    fun destroy() {
        rec?.destroy()
        rec = null
        onListening(false)
    }

    private fun first(b: Bundle?): String? =
        b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }
}
