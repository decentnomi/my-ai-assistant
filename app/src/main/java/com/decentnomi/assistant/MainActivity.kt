package com.decentnomi.assistant

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this)
        tv.text = "Salam! Main aap ka AI Assistant hoon."
        tv.textSize = 24f
        tv.gravity = Gravity.CENTER
        setContentView(tv)
    }
}
