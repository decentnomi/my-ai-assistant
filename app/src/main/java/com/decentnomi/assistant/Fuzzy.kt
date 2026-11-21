package com.decentnomi.assistant

import android.content.Context
import android.content.Intent

object Fuzzy {

    private val raw = "क:k,ख:k,ग:g,घ:g,ङ:n,च:ch,छ:ch,ज:j,झ:j,ञ:n,ट:t,ठ:t,ड:d,ढ:d,ण:n,त:t,थ:t,द:d,ध:d,न:n," +
        "प:p,फ:f,ब:b,भ:b,म:m,य:y,र:r,ल:l,व:w,श:s,ष:s,स:s,ह:h," +
        "ب:b,پ:p,ت:t,ٹ:t,ث:s,ج:j,چ:ch,خ:k,د:d,ڈ:d,ذ:j,ر:r,ڑ:r,ز:j,ژ:j,س:s,ش:s,ص:s,ض:j,ط:t,ظ:j," +
        "غ:g,ف:f,ق:k,ک:k,گ:g,ل:l,م:m,ن:n,ں:n,و:w,ہ:h,ھ:h,ی:y,ے:y,ئ:y"

    private val tbl: HashMap<Char, String> = HashMap<Char, String>().apply {
        for (p in raw.split(",")) {
            if (p.length >= 3) put(p[0], p.substring(2))
        }
    }

    private fun skel(s: String): String {
        val sb = StringBuilder()
        for (ch in s.lowercase()) {
            if (ch.code < 128) {
                if (ch.isLetter()) sb.append(ch)
            } else {
                val m = tbl[ch]
                if (m != null) sb.append(m)
            }
        }
        var x = sb.toString()
        x = x.replace("ph", "f").replace("ch", "x").replace("sh", "s").replace("kh", "k")
        x = x.replace('c', 'k').replace('q', 'k').replace('v', 'w').replace('z', 'j')
        val out = StringBuilder()
        for (ch in x) {
            if ("aeiouhy".indexOf(ch) >= 0) continue
            if (out.isNotEmpty() && out[out.length - 1] == ch) continue
            out.append(ch)
        }
        return out.toString()
    }

    private fun lev(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return dp[b.length]
    }

    fun fallback(c: Context, name: String): String? {
        val q = skel(name)
        if (q.length < 2) return "App " + name + " nahi mili"
        val pm = c.packageManager
        val i = Intent(Intent.ACTION_MAIN)
        i.addCategory(Intent.CATEGORY_LAUNCHER)
        var bestPkg: String? = null
        var bestD = 99
        for (ri in pm.queryIntentActivities(i, 0)) {
            val sk = skel(ri.loadLabel(pm).toString())
            if (sk.length < 2) continue
            val d = lev(q, sk)
            if (d < bestD) {
                bestD = d
                bestPkg = ri.activityInfo.packageName
            }
        }
        val limit = maxOf(1, q.length / 4)
        val pkg = bestPkg
        if (pkg == null || bestD > limit) return "App " + name + " nahi mili"
        val li = pm.getLaunchIntentForPackage(pkg) ?: return "App " + name + " nahi mili"
        li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            c.startActivity(li)
            null
        } catch (e: Exception) {
            "Nahi khul saka: " + (e.message ?: "")
        }
    }
}
