package com.decentnomi.assistant

object FastPath {

    private val onW = setOf("on", "chalu", "jalao", "jala", "start", "चालू", "ऑन", "जलाओ", "जला", "آن", "چالو", "جلاؤ")
    private val offW = setOf("off", "band", "bujhao", "bujha", "बंद", "बन्द", "ऑफ", "बुझाओ", "बुझा", "آف", "بند", "بجھاؤ")
    private val upW = setOf("up", "badhao", "badha", "zyada", "high", "increase", "बढ़ाओ", "बढ़ा", "ज्यादा", "ज़्यादा", "بڑھاؤ", "زیادہ")
    private val downW = setOf("down", "kam", "low", "decrease", "घटाओ", "घटा", "कम", "کم", "گھٹاؤ")
    private val fullW = setOf("full", "max", "maximum", "फुल", "فل")
    private val openW = setOf("open", "kholo", "khol", "खोलो", "खोल", "ओपन", "کھولو", "کھول", "اوپن")
    private val filler = setOf(
        "karo", "kro", "kar", "do", "de", "dena", "please", "plz", "app", "ko", "ka", "ki", "ek", "zara",
        "जी", "करो", "कर", "दो", "दे", "एप", "ऐप", "को", "का", "की", "ज़रा", "کرو", "کر", "دو", "ایپ", "کو", "جی"
    )

    private val aliases: List<Pair<String, List<String>>> = listOf(
        Pair("WhatsApp", listOf("whatsapp", "whats app", "व्हाट्सएप", "व्हाट्सऐप", "व्हाट्सअप", "वाट्सएप", "वॉट्सऐप", "واٹس ایپ", "واٹسایپ", "واٹس اپ")),
        Pair("YouTube", listOf("youtube", "you tube", "यूट्यूब", "यूट्यब", "یوٹیوب")),
        Pair("Facebook", listOf("facebook", "फेसबुक", "فیس بک", "فیسبک")),
        Pair("Instagram", listOf("instagram", "इंस्टाग्राम", "انسٹاگرام")),
        Pair("Snapchat", listOf("snapchat", "snap chat", "स्नैपचैट", "सनैपचैट", "سنیپ چیٹ")),
        Pair("Camera", listOf("camera", "कैमरा", "کیمرہ", "کیمرا")),
        Pair("Chrome", listOf("chrome", "क्रोम", "کروم")),
        Pair("Settings", listOf("settings", "सेटिंग", "سیٹنگ")),
        Pair("Telegram", listOf("telegram", "टेलीग्राम", "ٹیلیگرام")),
        Pair("TikTok", listOf("tiktok", "tik tok", "टिकटॉक", "ٹک ٹاک")),
        Pair("Messenger", listOf("messenger", "मैसेंजर", "میسنجر")),
        Pair("Gallery", listOf("gallery", "गैलरी", "گیلری")),
        Pair("Maps", listOf("maps", "मैप्स"))
    )

    private fun done(err: String?, ok: String): Reply {
        val m = err ?: ok
        return Reply(m, m, "en-IN", err != null)
    }

    private fun brt(res: String?, ok: String): Reply {
        if (res != null && res.startsWith("Brightness ") && res.length > 11 && res[11].isDigit()) return Reply(res, res, "en-IN", false)
        return done(res, ok)
    }

    fun run(text: String, a: Actions): Reply? {
        val s = text.lowercase().trim()
        val toks = s.split(" ", ",", ".", "!", "?", "\u0964", "\u061F").filter { it.isNotBlank() }
        fun t(vararg w: String): Boolean = toks.any { it in w }
        fun any(vararg w: String): Boolean = w.any { s.contains(it) }
        val on = toks.any { it in onW }
        val off = toks.any { it in offW }
        val up = toks.any { it in upW }
        val down = toks.any { it in downW }
        val full = toks.any { it in fullW }

        val screenW = any("screen", "display", "स्क्रीन", "डिस्प्ले", "سکرین", "ڈسپلے")
        if ((screenW && off) || t("lock", "लॉक", "لاک")) {
            return done(a.system("lock"), "Screen band kar rahi hoon")
        }

        if (any("torch", "flashlight", "flash light", "टॉर्च", "टार्च", "فلیش", "ٹارچ") && (on || off)) {
            val want = !off
            return done(a.toggle("flashlight", want), if (want) "Torch on kar di" else "Torch band kar di")
        }

        if (any("brightness", "ब्राइटनेस", "برائٹنیس")) {
            if (full) return brt(a.brightness(100, ""), "Brightness full")
            if (down) return brt(a.brightness(-1, "down"), "Brightness kam")
            if (up) return brt(a.brightness(-1, "up"), "Brightness zyada")
            return null
        }

        if (any("volume", "वॉल्यूम", "वॉलयूम", "आवाज़", "आवाज", "وولیوم", "والیوم", "آواز")) {
            if (t("mute", "म्यूट", "میوٹ") || off) return done(a.volume(-1, "mute"), "Mute kar diya")
            if (full) return done(a.volume(100, ""), "Volume full")
            if (down) return done(a.volume(-1, "down"), "Volume kam")
            if (up) return done(a.volume(-1, "up"), "Volume zyada")
            return null
        }

        if (any("wifi", "wi-fi", "wi fi", "वाईफाई", "वाई-फाई", "वाई फाई", "وائی فائی") && (on || off)) {
            val want = !off
            return done(a.toggle("wifi", want), if (want) "WiFi on kar rahi hoon" else "WiFi band kar rahi hoon")
        }

        if (any("bluetooth", "ब्लूटूथ", "ब्लुटूथ", "بلوٹوتھ") && (on || off)) {
            val want = !off
            return done(a.toggle("bluetooth", want), if (want) "Bluetooth on kar diya" else "Bluetooth band kar diya")
        }

        if (any("battery", "बैटरी", "بیٹری")) {
            val m = a.info("battery") ?: return null
            return Reply(m, m, "en-IN")
        }

        if (any("kitne baje", "what time", "time kya", "टाइम क्या", "कितने बजे", "کتنے بجے", "ٹائم کیا")) {
            val m = a.info("time") ?: return null
            return Reply(m, m, "en-IN")
        }

        if (t("home", "होम", "ہوم") && t("jao", "go", "chalo", "screen", "जाओ", "जाइए", "چلو", "جاؤ")) {
            return done(a.system("home"), "Home par aa gayi")
        }

        if (toks.any { it in openW }) {
            var name = ""
            for (al in aliases) {
                if (al.second.any { s.contains(it) }) {
                    name = al.first
                    break
                }
            }
            if (name.isBlank()) {
                name = toks.filter { it !in openW && it !in filler }.joinToString(" ")
            }
            if (name.isBlank()) return null
            val err = a.openApp(name)
            if (err == null) return Reply("Theek hai, " + name + " khol rahi hoon", "Theek hai, " + name + " khol rahi hoon", "en-IN", false)
            if (err.startsWith("App ")) return null
            return Reply(err, err, "en-IN")
        }

        return null
    }
}
