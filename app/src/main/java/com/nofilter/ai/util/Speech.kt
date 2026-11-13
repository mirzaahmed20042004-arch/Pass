package com.nofilter.ai.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import com.nofilter.ai.ui.Ui
import java.util.Locale

object Speech {

    class Tts(private val ctx: Context) : TextToSpeech.OnInitListener {
        private var tts: TextToSpeech? = null
        var ready = false
            private set

        fun init() {
            if (tts == null) tts = TextToSpeech(ctx.applicationContext, this)
        }

        override fun onInit(status: Int) {
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.language = Locale.ENGLISH
            }
        }

        fun speak(text: String) {
            if (!ready) { init(); return }
            val clean = text.replace(Regex("[*_`#>\\[\\]()~]"), " ")
            tts?.speak(clean.take(3000), TextToSpeech.QUEUE_FLUSH, null, "nf")
        }

        fun stop() { tts?.stop() }

        fun shutdown() { tts?.shutdown() }
    }

    const val REQ_STT = 9917

    fun startStt(activity: Activity) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.ENGLISH.toString())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now…")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        try {
            activity.startActivityForResult(intent, REQ_STT)
        } catch (e: Exception) {
            Ui.toast(activity, "Speech recognition not available on this device")
        }
    }

    fun sttResult(data: Intent?): String {
        if (data == null) return ""
        val res = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        return res?.firstOrNull() ?: ""
    }
}
