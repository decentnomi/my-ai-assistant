package com.decentnomi.assistant

import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Bus {
    val lines = ArrayList<String>()
    var heard = 0
    var wakes = 0
    var running = false
    var onChange: (() -> Unit)? = null
    private val h = Handler(Looper.getMainLooper())
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun add(s: String) {
        lines.add(fmt.format(Date()) + "  " + s)
        while (lines.size > 40) lines.removeAt(0)
        changed()
    }

    fun changed() {
        h.post { onChange?.invoke() }
    }
}
