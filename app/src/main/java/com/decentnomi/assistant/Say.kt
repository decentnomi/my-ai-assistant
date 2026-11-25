package com.decentnomi.assistant

object Say {
    private val M: Map<String, String> = mapOf(
        "awaz" to "आवाज़", "badha" to "बढ़ा", "di" to "दी", "kam" to "कम", "kar" to "कर",
        "full" to "फुल", "torch" to "टॉर्च", "jala" to "जला", "band" to "बंद", "flash" to "फ्लैश",
        "nahi" to "नहीं", "mila" to "मिला", "brightness" to "ब्राइटनेस", "low" to "लो",
        "abhi" to "अभी", "baje" to "बजे", "hain" to "हैं", "battery" to "बैटरी",
        "percent" to "प्रतिशत", "hai" to "है", "charge" to "चार्ज", "ho" to "हो", "rahi" to "रही",
        "khol" to "खोल", "raha" to "रहा", "hun" to "हूँ", "ko" to "को", "call" to "कॉल",
        "ye" to "ये", "sms" to "एसएमएस", "bhejun" to "भेजूँ", "haan" to "हाँ", "ya" to "या",
        "bolen" to "बोलें", "theek" to "ठीक", "cancel" to "कैंसिल", "diya" to "दिया", "bhej" to "भेज",
        "kisko" to "किसको", "karni" to "करनी", "kaun" to "कौन", "si" to "सी", "app" to "ऐप",
        "kholni" to "खोलनी", "decent" to "डिसेंट", "setup" to "सेटअप", "ma" to "में",
        "ijazat" to "इजाज़त", "den" to "दें", "naam" to "नाम", "ki" to "की", "ka" to "का",
        "contact" to "कॉन्टैक्ट", "bhejne" to "भेजने", "masla" to "मसला", "aaya" to "आया",
        "internet" to "इंटरनेट", "gemini" to "जेमिनी", "key" to "की", "galat" to "ग़लत",
        "masroof" to "मसरूफ़", "thori" to "थोड़ी", "der" to "देर", "baad" to "बाद", "try" to "ट्राई",
        "karen" to "करें", "pehle" to "पहले", "am" to "ए एम", "pm" to "पी एम", "aur" to "और",
        "mein" to "में", "se" to "से", "ke" to "के", "ek" to "एक", "main" to "मैं"
    )

    fun hi(s: String): String {
        val sb = StringBuilder()
        for (m in Regex("[A-Za-z]+|[^A-Za-z]+").findAll(s)) {
            val w = m.value
            if (w[0].code < 128 && w[0].isLetter()) {
                sb.append(M[w.lowercase()] ?: w)
            } else {
                sb.append(w)
            }
        }
        return sb.toString()
    }
}
