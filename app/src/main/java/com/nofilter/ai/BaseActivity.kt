package com.nofilter.ai

import android.app.Activity
import android.os.Bundle
import com.nofilter.ai.util.Prefs

open class BaseActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        Prefs.init(applicationContext)
        Prefs.applyTheme(this)
        super.onCreate(savedInstanceState)
    }
}
