package com.decentnomi.assistant

object Roman {
    private val cons: Map<Char, String> = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "n",
        'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "n",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "f", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v",
        'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h"
    )

    private val indep: Map<Char, String> = mapOf(
        'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee", 'उ' to "u", 'ऊ' to "oo",
        'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au", 'ऑ' to "o", 'ऋ' to "ri"
    )

    private val matra: Map<Char, String> = mapOf(
        'ा' to "aa", 'ि' to "i", 'ी' to "ee", 'ु' to "u", 'ू' to "oo",
        'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au", 'ॉ' to "o", 'ृ' to "ri"
    )

    private fun dev(c: Char): Boolean = c in '\u0900'..'\u097F'

    fun of(s: String): String {
        var has = false
        for (ch in s) {
            if (dev(ch)) {
                has = true
                break
            }
        }
        if (!has) return s
        val t = s.replace("\u093C", "")
        val sb = StringBuilder()
        var i = 0
        while (i < t.length) {
            val c = t[i]
            val cm = cons[c]
            if (cm != null) {
                sb.append(cm)
                val n = if (i + 1 < t.length) t[i + 1] else ' '
                val mt = matra[n]
                if (mt != null) {
                    sb.append(mt)
                    i += 2
                    continue
                }
                if (n == '\u094D') {
                    i += 2
                    continue
                }
                if (dev(n)) sb.append("a")
                i++
                continue
            }
            val iv = indep[c]
            if (iv != null) {
                sb.append(iv)
                i++
                continue
            }
            if (c == '\u0902' || c == '\u0901') {
                sb.append("n")
                i++
                continue
            }
            if (c == '\u0903') {
                sb.append("h")
                i++
                continue
            }
            if (c == '\u0964') {
                sb.append(".")
                i++
                continue
            }
            if (dev(c)) {
                i++
                continue
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }
}
