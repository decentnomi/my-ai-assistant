package com.decentnomi.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Gem {
    private val hist = ArrayList<Pair<String, String>>()
    private val MODELS = listOf("gemini-flash-lite-latest", "gemini-flash-latest", "gemini-2.5-flash")

    private fun post(model: String, key: String, body: String): Pair<Int, String> {
        val u = URL("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent")
        val c = u.openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 8000
        c.readTimeout = 20000
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

    private fun build(sys: String, said: String, think: Boolean): String {
        val contents = JSONArray()
        for (h in hist.takeLast(8)) {
            contents.put(
                JSONObject().put("role", h.first)
                    .put("parts", JSONArray().put(JSONObject().put("text", h.second)))
            )
        }
        contents.put(
            JSONObject().put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", said)))
        )
        val gc = JSONObject().put("maxOutputTokens", 220).put("temperature", 0.8)
        if (think) gc.put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
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
            "NEVER use Devanagari or Urdu or Arabic script. Keep replies to one or two short sentences because they are spoken aloud. " +
            "No emojis, no markdown, no lists. If asked to do a phone action you cannot do, briefly say you cannot do that yet."
        for (m in MODELS) {
            for (think in listOf(true, false)) {
                val r = try {
                    post(m, key, build(sys, said, think))
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
                    val out = Roman.of(t).replace(Regex("[*#_`]"), "").trim()
                    if (out.isEmpty()) return "Gemini ne jawab nahi diya"
                    hist.add(Pair("user", said))
                    hist.add(Pair("model", out))
                    while (hist.size > 12) hist.removeAt(0)
                    return out
                }
                if (r.first == 400 && think && !r.second.contains("API key")) continue
                if (r.first == 400 || r.first == 403) {
                    if (r.second.contains("API key") || r.first == 403) return "Gemini key galat hai"
                }
                if (r.first == 404 || r.first == 429 || r.first == 500 || r.first == 503) break
                return "Gemini error " + r.first
            }
        }
        return "Gemini abhi masroof hai, thori der baad try karen"
    }
}
