package com.decentnomi.assistant

import android.content.SharedPreferences

object WakeWords {
    val DEFAULT: String = "decent, descent, disent, desent, deesent, dicent, " +
        "\u0921\u093F\u0938\u0947\u0902\u091F, \u0921\u0940\u0938\u0947\u0902\u091F, " +
        "\u0921\u0947\u0938\u0947\u0902\u091F, \u0921\u093F\u0938\u0947\u0928\u094D\u091F, " +
        "\u0688\u06CC\u0633\u0646\u0679"

    fun list(p: SharedPreferences): List<String> {
        var s = p.getString("wakewords", "") ?: ""
        if (s.isBlank()) s = DEFAULT
        return s.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
    }
}
