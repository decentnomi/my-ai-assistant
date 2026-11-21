package com.decentnomi.assistant

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var statusView: TextView
    private lateinit var chatView: TextView
    private lateinit var inputBox: EditText
    private lateinit var keyBox: EditText
    private lateinit var modelBox: EditText
    private lateinit var nameBox: EditText
    private lateinit var langBox: EditText
    private val prefs by lazy { getSharedPreferences("assistant", Context.MODE_PRIVATE) }

    private fun check(text: String, key: String, def: Boolean): CheckBox {
        val cb = CheckBox(this)
        cb.text = text
        cb.isChecked = prefs.getBoolean(key, def)
        cb.setOnCheckedChangeListener { _, v -> prefs.edit().putBoolean(key, v).apply() }
        return cb
    }

    private fun field(hint: String, value: String, secret: Boolean): EditText {
        val e = EditText(this)
        e.hint = hint
        e.setText(value)
        if (secret) e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        return e
    }

    private fun toService(text: String?, listen: Boolean) {
        if (!prefs.getBoolean("wake", false)) {
            statusView.text = "Pehle neeche 'Mode C' tick karen (service chalu ho)"
            return
        }
        val i = Intent(this, WakeService::class.java)
        if (text != null) i.putExtra("text", text)
        if (listen) i.putExtra("listen", true)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
    }

    private fun render() {
        chatView.text = ChatLog.lines.takeLast(14).joinToString("\n\n")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(pad, pad * 2, pad, pad)

        val title = TextView(this)
        title.text = "Hello Nomi"
        title.textSize = 28f
        statusView = TextView(this)
        statusView.textSize = 14f
        chatView = TextView(this)
        chatView.textSize = 16f
        chatView.minLines = 4

        inputBox = EditText(this)
        inputBox.hint = "Yahan likh kar bhi hukm de sakte hain"
        val sendBtn = Button(this)
        sendBtn.text = "Bhejo"
        sendBtn.setOnClickListener {
            val t = inputBox.text.toString().trim()
            if (t.isNotBlank()) {
                inputBox.setText("")
                toService(t, false)
            }
        }
        val micBtn = Button(this)
        micBtn.text = "Mic"
        micBtn.setOnClickListener { toService(null, true) }
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.addView(inputBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(sendBtn)
        row.addView(micBtn)

        keyBox = field("Gemini API key", prefs.getString("key", "") ?: "", true)
        modelBox = field("Model ka naam", prefs.getString("model", "gemini-flash-lite-latest") ?: "gemini-flash-lite-latest", false)
        nameBox = field("Aap ka naam (Nomi aap ko is naam se bulaye)", prefs.getString("uname", "") ?: "", false)
        langBox = field("Sunne ki zubaan: hi-IN, ur-PK, pa-IN, en-US, zh-CN", prefs.getString("rlang", "hi-IN") ?: "hi-IN", false)

        val saveBtn = Button(this)
        saveBtn.text = "Save karen"
        saveBtn.setOnClickListener {
            prefs.edit()
                .putString("key", keyBox.text.toString().trim())
                .putString("model", modelBox.text.toString().trim())
                .putString("uname", nameBox.text.toString().trim())
                .putString("rlang", langBox.text.toString().trim())
                .apply()
            statusView.text = "Save ho gaya (zubaan badli ho to service band karke dobara chalayen)"
        }

        val accBtn = Button(this)
        accBtn.text = "Accessibility ki ijazat (ek baar)"
        accBtn.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }

        root.addView(title)
        root.addView(statusView)
        root.addView(chatView)
        root.addView(row)
        root.addView(check("Girlfriend mode (pyaar bhara andaz)", "gf", false))
        root.addView(check("Professional mode (sanjeeda, mukhtasir)", "pro", false))
        root.addView(check("WhatsApp message khud send karen", "autosend", true))
        root.addView(check("Message bhejne se pehle poochen (haan/nahi)", "confirm", false))
        WakeUi.add(this, root)
        root.addView(keyBox)
        root.addView(modelBox)
        root.addView(nameBox)
        root.addView(langBox)
        root.addView(saveBtn)
        root.addView(accBtn)

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)

        val need = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS
        ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), 1)
    }

    override fun onResume() {
        super.onResume()
        ChatLog.onChange = { render() }
        render()
    }

    override fun onPause() {
        ChatLog.onChange = null
        super.onPause()
    }
}
