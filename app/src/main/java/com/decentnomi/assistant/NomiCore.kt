package com.decentnomi.assistant

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Reply(val display: String, val speak: String, val lang: String)

object ChatLog {
    val lines = ArrayList<String>()
    var onChange: (() -> Unit)? = null
    private val h = Handler(Looper.getMainLooper())

    fun add(user: Boolean, text: String) {
        lines.add((if (user) "Aap: " else "Nomi: ") + text)
        while (lines.size > 60) lines.removeAt(0)
        h.post { onChange?.invoke() }
    }
}

object NomiCore {
    private val hist = ArrayList<Pair<String, String>>()
    private var pending: JSONObject? = null

    private val BASE = """
Tumhara naam Hello Nomi hai, user ki personal phone assistant. Neeche user ki bolti hui baat ka speech-recognition text aata hai. Wo kisi bhi zubaan ma ho sakta hai: Urdu, Hindi, Punjabi, English, Roman Urdu, Chinese, Arabic waghera, ya in ka mix. Text ma ghaltiyan ho sakti hain (khaas kar naam), samajhdari se theek karo. Sirf ek JSON object do, aur kuch nahi.
ZUBAAN KE QAYDE:
- Jawab hamesha user ki zubaan ma do.
- Hindi/Urdu/Punjabi/Hinglish ho to "reply" Roman letters ma likho (Roman Urdu), aur "say" ma wahi jumla Devanagari (Hindi script) ma likho taake awaz sahi nikle, aur "lang":"hi-IN".
- English ho to "reply" aur "say" dono English ma, "lang":"en-US".
- Chinese, Arabic ya koi aur zubaan ho to "reply" aur "say" dono usi zubaan aur script ma, "lang" us zubaan ka code (zh-CN, ar-SA waghera).
- "reply" sirf 1 chhota jumla (zyada se zyada 2). Agar koi kaam ho raha ho to chhota tasdeeqi jumla, jaise "Theek hai, YouTube khol raha hoon".
- Contact aur app ke naam hamesha English (Latin) letters ma likho.
Har JSON ma "reply", "say", "lang" zaroor hon. Agar hukm adhoora ho (message ka matn ya naam na ho) to action "chat" ma chhota sawal pooch lo. Mumkin actions:
{"action":"call","name":"Ali"}
{"action":"whatsapp","name":"Ali","message":"user ki taraf se chhota, qudrati message, usi zubaan ma jis ma user ne kaha (Hindi/Urdu ho to Roman Urdu ma), main ke andaz ma"}
{"action":"sms","name":"Ali","message":"..."}
{"action":"open_app","app":"YouTube"}
{"action":"toggle","target":"wifi|bluetooth|mobile_data|airplane|hotspot|location|flashlight|rotate|dnd|nfc","on":true}
{"action":"brightness","level":0 se 100} ya {"action":"brightness","dir":"up|down"}
{"action":"volume","level":0 se 100} ya {"action":"volume","dir":"up|down|mute"}
{"action":"ringer","mode":"silent|vibrate|normal"}
{"action":"media","cmd":"play|pause|next|previous"}
{"action":"alarm","hour":0 se 23,"minute":0 se 59}
{"action":"timer","seconds":300}
{"action":"system","cmd":"home|back|recents|notifications|screenshot|lock|power_menu"}
{"action":"settings","target":"wifi|bluetooth|display|sound|battery|location|apps|airplane|data|nfc|date|main"}
{"action":"info","kind":"battery|time"}
{"action":"search","query":"..."}
{"action":"maps","query":"..."}
{"action":"camera"}
{"action":"chat"}   (baqi har baat: sirf reply/say ma jawab)
"""

    private fun prompt(p: SharedPreferences): String {
        val gf = p.getBoolean("gf", false)
        val pro = p.getBoolean("pro", false)
        val persona = if (gf && pro) {
            "Andaz: pyaar bhari aur care karne wali, lekin samajhdar aur professional. Kaam ki baat saaf aur mukhtasir, lehja narm aur apnaiyat wala."
        } else if (gf) {
            "Andaz: Girlfriend mode. Pyaar, care aur halka phulka mazaaq, apnaiyat bhara lehja."
        } else if (pro) {
            "Andaz: Professional mode. Sanjeeda, adab wala, bilkul mukhtasir."
        } else {
            "Andaz: dostana aur mukhtasir assistant."
        }
        val nm = (p.getString("uname", "") ?: "").trim()
        val who = if (nm.isNotBlank()) " User ka naam " + nm + " hai, kabhi kabhi us naam se bulao." else ""
        return BASE + "\n" + persona + who
    }

    fun remember(user: String, modelRaw: String) {
        hist.add(Pair("user", user))
        hist.add(Pair("model", modelRaw))
        while (hist.size > 10) {
            hist.removeAt(0)
            hist.removeAt(0)
        }
    }

    private fun call(key: String, model: String, text: String, thinkingOff: Boolean, p: SharedPreferences): Pair<Int, String> {
        return try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent")
            val c = url.openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("x-goog-api-key", key)
            c.connectTimeout = 10000
            c.readTimeout = 25000
            c.doOutput = true
            val body = JSONObject()
            body.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt(p)))))
            val contents = JSONArray()
            for (x in ArrayList(hist)) {
                contents.put(JSONObject().put("role", x.first).put("parts", JSONArray().put(JSONObject().put("text", x.second))))
            }
            contents.put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", text))))
            body.put("contents", contents)
            val gen = JSONObject().put("responseMimeType", "application/json").put("temperature", 0.5)
            if (thinkingOff) gen.put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
            body.put("generationConfig", gen)
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            Pair(code, stream.bufferedReader().readText())
        } catch (e: Exception) {
            Pair(-1, "Masla: " + e.message)
        }
    }

    fun ask(p: SharedPreferences, text: String): Pair<Int, String> {
        val key = p.getString("key", "") ?: ""
        val model = (p.getString("model", "gemini-flash-lite-latest") ?: "gemini-flash-lite-latest").ifBlank { "gemini-flash-lite-latest" }
        var r = call(key, model, text, true, p)
        if (r.first == 400) r = call(key, model, text, false, p)
        if (r.first == 404 && model != "gemini-flash-latest") {
            r = call(key, "gemini-flash-latest", text, true, p)
            if (r.first == 400) r = call(key, "gemini-flash-latest", text, false, p)
        }
        return r
    }

    fun extractText(body: String): String? {
        return try {
            JSONObject(body).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
        } catch (e: Exception) {
            null
        }
    }

    private fun exec(j: JSONObject, a: Actions, p: SharedPreferences, forceSend: Boolean): String? {
        return when (j.optString("action", "chat")) {
            "call" -> a.call(j.optString("name"))
            "whatsapp" -> a.whatsapp(j.optString("name"), j.optString("message"), forceSend || p.getBoolean("autosend", true))
            "sms" -> a.sms(j.optString("name"), j.optString("message"))
            "open_app" -> a.openApp(j.optString("app"))
            "toggle" -> a.toggle(j.optString("target"), j.optBoolean("on", true))
            "brightness" -> a.brightness(j.optInt("level", -1), j.optString("dir"))
            "volume" -> a.volume(j.optInt("level", -1), j.optString("dir"))
            "ringer" -> a.ringer(j.optString("mode"))
            "media" -> a.media(j.optString("cmd"))
            "alarm" -> a.alarm(j.optInt("hour", 0), j.optInt("minute", 0))
            "timer" -> a.timer(j.optInt("seconds", 60))
            "system" -> a.system(j.optString("cmd"))
            "settings" -> a.settings(j.optString("target"))
            "info" -> a.info(j.optString("kind"))
            "search" -> a.search(j.optString("query"))
            "maps" -> a.maps(j.optString("query"))
            "camera" -> a.camera()
            else -> null
        }
    }

    fun dispatch(raw: String, a: Actions, p: SharedPreferences): Reply {
        val fence = "\u0060\u0060\u0060"
        val clean = raw.trim().removePrefix(fence + "json").removePrefix(fence).removeSuffix(fence).trim()
        val j = try {
            JSONObject(clean)
        } catch (e: Exception) {
            return Reply(clean, clean, "")
        }
        val reply = j.optString("reply")
        val say = j.optString("say").ifBlank { reply }
        val lang = j.optString("lang")
        val action = j.optString("action", "chat")
        if (p.getBoolean("confirm", false) && (action == "whatsapp" || action == "sms")) {
            pending = j
            val q = "Message likha: " + j.optString("message") + ". Bhej doon? (haan ya nahi)"
            return Reply(q, q, "en-IN")
        }
        val out = exec(j, a, p, false)
        if (out != null) return Reply(out, out, "en-IN")
        val shown = reply.ifBlank { "Theek hai" }
        return Reply(shown, say.ifBlank { shown }, lang)
    }

    private val yes = setOf(
        "yes", "haan", "han", "haa", "ha", "ji", "jee", "ok", "okay", "bhej", "bhejo", "send", "kar", "kardo",
        "हां", "हाँ", "हा", "जी", "भेज", "भेजो", "ठीक", "ओके", "ہاں", "جی", "بھیج", "بھیجو", "ٹھیک"
    )
    private val no = setOf(
        "no", "nahi", "nahin", "nai", "cancel", "ruko", "ruk", "mat",
        "नहीं", "नही", "ना", "मत", "रुको", "रुक", "कैंसिल", "نہیں", "نا", "مت", "رکو"
    )

    fun tryPending(text: String, a: Actions, p: SharedPreferences): Reply? {
        val pj = pending ?: return null
        pending = null
        val toks = text.lowercase().split(" ", ",", ".", "!", "?", "\u0964").filter { it.isNotBlank() }
        if (toks.any { no.contains(it) }) return Reply("Theek hai, cancel kar diya", "Theek hai, cancel kar diya", "en-IN")
        if (toks.any { yes.contains(it) }) {
            val out = exec(pj, a, p, true)
            val m = out ?: "Bhej diya"
            return Reply(m, m, "en-IN")
        }
        return null
    }
}
