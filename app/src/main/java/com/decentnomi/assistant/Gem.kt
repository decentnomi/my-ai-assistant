package com.decentnomi.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Gem {
    var last = ""
    var lastSay = ""
    private var goodModel = ""
    private var goodMode = -1
    private val hist = ArrayList<Pair<String, String>>()
    private val MODELS = listOf(
        "gemini-3.5-flash-lite",
        "gemini-flash-lite-latest",
        "gemini-3.1-flash-lite",
        "gemini-flash-latest",
        "gemini-2.5-flash"
    )

    private fun post(model: String, key: String, body: String): Pair<Int, String> {
        val u = URL("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent")
        val c = u.openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 3000
        c.readTimeout = 4000
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("x-goog-api-key", key)
        c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = c.responseCode
        val st = if (code >= 400) c.errorStream else c.inputStream
        val txt = st?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        c.disconnect()
        return Pair(code, txt)
    }

    private fun build(sys: String, said: String, mode: Int): String {
        val contents = JSONArray()
        for (h in hist.takeLast(6)) {
            contents.put(
                JSONObject().put("role", h.first)
                    .put("parts", JSONArray().put(JSONObject().put("text", h.second)))
            )
        }
        contents.put(
            JSONObject().put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", said)))
        )
        val gc = JSONObject().put("maxOutputTokens", 220).put("responseMimeType", "application/json")
        if (mode == 0) gc.put("thinkingConfig", JSONObject().put("thinkingLevel", "minimal"))
        if (mode == 1) gc.put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
        return JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sys))))
            .put("contents", contents)
            .put("generationConfig", gc)
            .toString()
    }

    fun ask(ctx: Context, said: String): String {
        val p = ctx.getSharedPreferences("decent", Context.MODE_PRIVATE)
        val key = p.getString("gkey", "") ?: ""
        if (key.isBlank()) return "Gemini key nahi hai. Decent Setup ma key daalen"
        val gf = (p.getString("mode", "pro") ?: "pro") == "gf"
        val who = if (gf) {
            "You are Decent, a sweet, caring, playful girlfriend-style voice companion on the user's Android phone. Warm and affectionate but always appropriate."
        } else {
            "You are Decent, a polite, smart, professional voice assistant on the user's Android phone."
        }
        val sys = who + " The user speaks Urdu, Hindi or English, often mixed. Reply in the same language style as the user, " +
            "but ALWAYS write using English (Latin) letters only: Roman Urdu or English. " +
            "NEVER use Devanagari or Urdu or Arabic script. Reply in at most 20 words, one or two short sentences, because it is spoken aloud. " +
            "No emojis, no markdown, no lists. Never invent phone data such as battery level, time or location. Output ONLY a JSON object with two string fields named reply and say. reply uses Roman Urdu or English letters. say is the same sentence written in Devanagari Hindi script so a Hindi voice can speak it naturally, English brand words may stay in English letters. If asked to do a phone action you cannot do, briefly say you cannot do that yet."
        val t0 = System.currentTimeMillis()
        val attempts = ArrayList<Pair<String, Int>>()
        if (goodModel.isNotEmpty()) attempts.add(Pair(goodModel, goodMode))
        for (m in MODELS) for (md in 0..2) {
            val a = Pair(m, md)
            if (!attempts.contains(a)) attempts.add(a)
        }
        val dead = HashSet<String>()
        for (a in attempts) {
            if (dead.contains(a.first)) continue
            if (System.currentTimeMillis() - t0 > 12000) break
            val r = try {
                postRetry(a.first, key, build(sys, said, a.second))
            } catch (e: Exception) {
                return "Internet ya Gemini ma masla aaya"
            }
            if (r.first == 200) {
                val t = try {
                    JSONObject(r.second).getJSONArray("candidates").getJSONObject(0)
                        .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                } catch (e: Exception) {
                    ""
                }
                var rep = t; lastSay = ""; try { val jo = JSONObject(t); rep = jo.optString("reply", t); lastSay = jo.optString("say", "").trim() } catch (e: Exception) { }; val out = Roman.of(rep).replace(Regex("[*#_`]"), "").trim()
                if (out.isEmpty()) return "Gemini ne jawab nahi diya"
                goodModel = a.first
                goodMode = a.second
                last = a.first + "/" + a.second
                hist.add(Pair("user", said))
                hist.add(Pair("model", out))
                while (hist.size > 12) hist.removeAt(0)
                return out
            }
            if (r.first == 403 || r.second.contains("API_KEY_INVALID") || r.second.contains("API key not valid")) {
                return "Gemini key galat hai"
            }
            if (r.first == 404 || r.first == 429 || r.first == 500 || r.first == 503) {
                dead.add(a.first)
            }
        }
        return "Gemini abhi masroof hai, thori der baad try karen"
    }

    private fun postRetry(m: String, k: String, b: String): Pair<Int, String> {
        return try {
            post(m, k, b)
        } catch (e: Exception) {
            post(m, k, b)
        }
    }
}
