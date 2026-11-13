package com.nofilter.ai.util

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import com.nofilter.ai.R

object Prefs {
    private const val NAME = "nofilter_prefs"
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        if (!::sp.isInitialized) {
            sp = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        }
    }

    fun applyTheme(activity: Activity) {
        init(activity.applicationContext)
        activity.setTheme(if (theme == 1) R.style.Theme_NoFilter_Light else R.style.Theme_NoFilter)
    }

    var theme: Int
        get() = sp.getInt("theme", 0) // 0 = dark, 1 = light
        set(v) { sp.edit().putInt("theme", v).apply() }

    var webResearchDefault: Boolean
        get() = sp.getBoolean("web_default", true)
        set(v) { sp.edit().putBoolean("web_default", v).apply() }

    var offlineMode: Boolean
        get() = sp.getBoolean("offline", false)
        set(v) { sp.edit().putBoolean("offline", v).apply() }

    var fontScale: Float
        get() = sp.getFloat("font_scale", 1.0f)
        set(v) { sp.edit().putFloat("font_scale", v).apply() }

    var defaultPersonaId: Long
        get() = sp.getLong("persona_default", 0L)
        set(v) { sp.edit().putLong("persona_default", v).apply() }

    var ocrLang: String
        get() = sp.getString("ocr_lang", "eng") ?: "eng"
        set(v) { sp.edit().putString("ocr_lang", v).apply() }
}
