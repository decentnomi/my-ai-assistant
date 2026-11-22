package com.decentnomi.assistant

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val PINK = Color.parseColor("#FF4FD8")
    private val PINK_SOFT = Color.parseColor("#80FF4FD8")
    private val VIOLET = Color.parseColor("#8B3DFF")
    private val CARD = Color.parseColor("#B32A0D40")
    private val BUBBLE_ME = Color.parseColor("#E6A12DB0")
    private val BUBBLE_AI = Color.parseColor("#E61E0B38")

    private lateinit var bgImage: ImageView
    private lateinit var statusView: TextView
    private lateinit var chatBox: LinearLayout
    private lateinit var chatScroll: ScrollView
    private lateinit var settingsScroll: ScrollView
    private lateinit var inputRow: LinearLayout
    private lateinit var inputBox: EditText
    private lateinit var keyBox: EditText
    private lateinit var modelBox: EditText
    private lateinit var nameBox: EditText
    private lateinit var langBox: EditText
    private lateinit var wakeBox: EditText
    private val prefs by lazy { getSharedPreferences("assistant", Context.MODE_PRIVATE) }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun shape(color: Int, radius: Int, strokeColor: Int, strokeDp: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(radius).toFloat()
        if (strokeDp > 0) g.setStroke(dp(strokeDp), strokeColor)
        return g
    }

    private fun circle(): GradientDrawable {
        val g = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(PINK, VIOLET))
        g.shape = GradientDrawable.OVAL
        g.setStroke(dp(2), Color.parseColor("#FFB8F0"))
        return g
    }

    private fun field(hint: String, value: String, secret: Boolean): EditText {
        val e = EditText(this)
        e.hint = hint
        e.setText(value)
        e.setTextColor(Color.WHITE)
        e.setHintTextColor(Color.parseColor("#B9A6D6"))
        e.textSize = 15f
        e.background = shape(Color.parseColor("#40FFFFFF"), 14, 0, 0)
        e.setPadding(dp(14), dp(10), dp(14), dp(10))
        if (secret) e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.setMargins(0, dp(6), 0, dp(6))
        e.layoutParams = lp
        return e
    }

    private fun styleCheck(cb: CheckBox) {
        cb.setTextColor(Color.WHITE)
        cb.buttonTintList = ColorStateList.valueOf(PINK)
    }

    private fun check(text: String, key: String, def: Boolean): CheckBox {
        val cb = CheckBox(this)
        cb.text = text
        cb.isChecked = prefs.getBoolean(key, def)
        cb.setOnCheckedChangeListener { _, v -> prefs.edit().putBoolean(key, v).apply() }
        styleCheck(cb)
        return cb
    }

    private fun pinkButton(text: String): Button {
        val b = Button(this)
        b.text = text
        b.isAllCaps = false
        b.setTextColor(Color.WHITE)
        val g = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(PINK, VIOLET))
        g.cornerRadius = dp(26).toFloat()
        b.background = g
        val lp = LinearLayout.LayoutParams(MATCH, dp(50))
        lp.setMargins(0, dp(8), 0, dp(4))
        b.layoutParams = lp
        return b
    }

    private fun card(parent: LinearLayout, title: String): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(16), dp(14), dp(16), dp(14))
        c.background = shape(CARD, 22, PINK_SOFT, 1)
        val t = TextView(this)
        t.text = title
        t.setTextColor(Color.parseColor("#FF9BEA"))
        t.textSize = 15f
        t.setTypeface(null, Typeface.BOLD)
        c.addView(t)
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.setMargins(0, dp(8), 0, dp(8))
        parent.addView(c, lp)
        return c
    }

    private fun roundBtn(label: String, size: Int, sizeText: Float): TextView {
        val t = TextView(this)
        t.text = label
        t.textSize = sizeText
        t.setTextColor(Color.WHITE)
        t.gravity = Gravity.CENTER
        t.background = circle()
        val lp = LinearLayout.LayoutParams(dp(size), dp(size))
        lp.setMargins(dp(6), 0, 0, 0)
        t.layoutParams = lp
        return t
    }

    private fun toService(text: String?, listen: Boolean) {
        if (!prefs.getBoolean("wake", false)) {
            statusView.text = "Pehle Settings ma 'Always Listening' tick karen"
            return
        }
        val i = Intent(this, WakeService::class.java)
        if (text != null) i.putExtra("text", text)
        if (listen) i.putExtra("listen", true)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
    }

    private fun bubble(text: String, mine: Boolean): View {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(Color.WHITE)
        tv.textSize = 16f
        tv.setPadding(dp(14), dp(10), dp(14), dp(10))
        tv.background = shape(if (mine) BUBBLE_ME else BUBBLE_AI, 18, PINK_SOFT, 1)
        tv.maxWidth = (resources.displayMetrics.widthPixels * 0.78f).toInt()
        val lp = LinearLayout.LayoutParams(WRAP, WRAP)
        lp.gravity = if (mine) Gravity.END else Gravity.START
        lp.setMargins(0, dp(5), 0, dp(5))
        tv.layoutParams = lp
        return tv
    }

    private fun render() {
        chatBox.removeAllViews()
        for (line in ChatLog.lines.takeLast(40)) {
            val mine = line.startsWith("Aap: ")
            chatBox.addView(bubble(line.substringAfter(": "), mine))
        }
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun showSettings(on: Boolean) {
        settingsScroll.visibility = if (on) View.VISIBLE else View.GONE
        chatScroll.visibility = if (on) View.GONE else View.VISIBLE
        inputRow.visibility = if (on) View.GONE else View.VISIBLE
    }

    private fun applyWall() {
        val s = prefs.getString("wall", "") ?: ""
        if (s.isNotBlank()) {
            try {
                contentResolver.openInputStream(Uri.parse(s))?.use { ins ->
                    val opt = BitmapFactory.Options()
                    opt.inSampleSize = 4
                    val bm = BitmapFactory.decodeStream(ins, null, opt)
                    if (bm != null) {
                        bgImage.background = null
                        bgImage.setImageBitmap(bm)
                        return
                    }
                }
            } catch (e: Exception) {
            }
        }
        bgImage.setImageDrawable(null)
        bgImage.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#12051E"), Color.parseColor("#3B0F55"), Color.parseColor("#8A1478"))
        )
    }

    private fun pickWall() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "image/*"
        startActivityForResult(i, 77)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 77 && resultCode == RESULT_OK) {
            val u = data?.data
            if (u != null) {
                try {
                    contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) {
                }
                prefs.edit().putString("wall", u.toString()).apply()
                applyWall()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#14061F")
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val frame = FrameLayout(this)
        bgImage = ImageView(this)
        bgImage.scaleType = ImageView.ScaleType.CENTER_CROP
        frame.addView(bgImage, FrameLayout.LayoutParams(MATCH, MATCH))
        val shade = View(this)
        shade.setBackgroundColor(Color.parseColor("#66140620"))
        frame.addView(shade, FrameLayout.LayoutParams(MATCH, MATCH))
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        frame.addView(col, FrameLayout.LayoutParams(MATCH, MATCH))

        // header
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(18), dp(34), dp(14), dp(8))
        val titleBox = LinearLayout(this)
        titleBox.orientation = LinearLayout.VERTICAL
        val title = TextView(this)
        title.text = "DECENT"
        title.textSize = 32f
        title.setTextColor(Color.parseColor("#FFB8F0"))
        title.typeface = Typeface.create("cursive", Typeface.BOLD)
        title.setShadowLayer(22f, 0f, 0f, PINK)
        val sub = TextView(this)
        sub.text = "AI COMPANION"
        sub.textSize = 12f
        sub.setTextColor(Color.parseColor("#E9C8FF"))
        sub.letterSpacing = 0.25f
        titleBox.addView(title)
        titleBox.addView(sub)
        val wall = TextView(this)
        wall.text = "\uD83D\uDDBC"
        wall.textSize = 24f
        wall.setPadding(dp(10), dp(6), dp(10), dp(6))
        wall.setOnClickListener { pickWall() }
        val gear = TextView(this)
        gear.text = "\u2699"
        gear.textSize = 26f
        gear.setTextColor(Color.WHITE)
        gear.setPadding(dp(10), dp(6), dp(10), dp(6))
        gear.setOnClickListener { showSettings(settingsScroll.visibility != View.VISIBLE) }
        header.addView(titleBox, LinearLayout.LayoutParams(0, WRAP, 1f))
        header.addView(wall)
        header.addView(gear)
        col.addView(header)

        statusView = TextView(this)
        statusView.textSize = 13f
        statusView.setTextColor(Color.parseColor("#FFB8F0"))
        statusView.text = "\u25CF Online"
        statusView.setPadding(dp(18), 0, dp(18), dp(6))
        col.addView(statusView)

        // content area: chat and settings
        val area = FrameLayout(this)
        chatScroll = ScrollView(this)
        chatBox = LinearLayout(this)
        chatBox.orientation = LinearLayout.VERTICAL
        chatBox.setPadding(dp(14), dp(6), dp(14), dp(6))
        chatScroll.addView(chatBox)
        area.addView(chatScroll, FrameLayout.LayoutParams(MATCH, MATCH))

        settingsScroll = ScrollView(this)
        val sb = LinearLayout(this)
        sb.orientation = LinearLayout.VERTICAL
        sb.setPadding(dp(14), dp(6), dp(14), dp(20))
        settingsScroll.addView(sb)
        settingsScroll.visibility = View.GONE
        area.addView(settingsScroll, FrameLayout.LayoutParams(MATCH, MATCH))
        col.addView(area, LinearLayout.LayoutParams(MATCH, 0, 1f))

        keyBox = field("Gemini API key", prefs.getString("key", "") ?: "", true)
        modelBox = field("Model ka naam", prefs.getString("model", "gemini-flash-lite-latest") ?: "gemini-flash-lite-latest", false)
        nameBox = field("Aap ka naam", prefs.getString("uname", "") ?: "", false)
        langBox = field("Sunne ki zubaan: hi-IN, ur-PK, pa-IN, en-US, zh-CN", prefs.getString("rlang", "hi-IN") ?: "hi-IN", false)
        val wakeText = (prefs.getString("wakewords", "") ?: "").ifBlank { WakeWords.DEFAULT }
        wakeBox = field("Wake words (comma se alag)", wakeText, false)

        val c1 = card(sb, "\uD83D\uDD11  Gemini API Key")
        c1.addView(keyBox)
        c1.addView(modelBox)
        val c2 = card(sb, "\uD83C\uDF99  Voice & Wake Word")
        c2.addView(langBox)
        c2.addView(wakeBox)
        val c3 = card(sb, "\uD83D\uDC64  Your Name")
        c3.addView(nameBox)
        val c4 = card(sb, "\uD83D\uDC97  Personality Mode")
        c4.addView(check("Girlfriend mode (pyaar bhara andaz)", "gf", false))
        c4.addView(check("Professional mode (sanjeeda, mukhtasir)", "pro", false))
        val c5 = card(sb, "\uD83D\uDCAC  Messages")
        c5.addView(check("WhatsApp message khud send karen", "autosend", true))
        c5.addView(check("Message bhejne se pehle poochen (haan/nahi)", "confirm", false))
        val c6 = card(sb, "\uD83D\uDD14  Always Listening")
        WakeUi.add(this, c6)
        for (k in 0 until c6.childCount) {
            val v = c6.getChildAt(k)
            if (v is CheckBox) styleCheck(v)
        }
        val c7 = card(sb, "\uD83D\uDEE1  Permissions & Look")
        val accBtn = pinkButton("Accessibility ki ijazat")
        accBtn.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        c7.addView(accBtn)
        val wallBtn = pinkButton("Wallpaper chunen")
        wallBtn.setOnClickListener { pickWall() }
        c7.addView(wallBtn)
        val wallClr = pinkButton("Wallpaper default karen")
        wallClr.setOnClickListener {
            prefs.edit().putString("wall", "").apply()
            applyWall()
        }
        c7.addView(wallClr)
        val saveBtn = pinkButton("SAVE SETTINGS")
        saveBtn.setOnClickListener {
            prefs.edit()
                .putString("key", keyBox.text.toString().trim())
                .putString("model", modelBox.text.toString().trim())
                .putString("uname", nameBox.text.toString().trim())
                .putString("rlang", langBox.text.toString().trim())
                .putString("wakewords", wakeBox.text.toString().trim())
                .apply()
            statusView.text = "Save ho gaya (zubaan badli ho to Always Listening off/on karen)"
            showSettings(false)
        }
        sb.addView(saveBtn)

        // input row
        inputRow = LinearLayout(this)
        inputRow.orientation = LinearLayout.HORIZONTAL
        inputRow.gravity = Gravity.CENTER_VERTICAL
        inputRow.setPadding(dp(12), dp(8), dp(12), dp(14))
        inputBox = EditText(this)
        inputBox.hint = "Type a message..."
        inputBox.setTextColor(Color.WHITE)
        inputBox.setHintTextColor(Color.parseColor("#B9A6D6"))
        inputBox.background = shape(Color.parseColor("#B31E0B38"), 28, PINK_SOFT, 1)
        inputBox.setPadding(dp(18), dp(12), dp(18), dp(12))
        inputBox.maxLines = 3
        val sendBtn = roundBtn("\u27A4", 48, 20f)
        sendBtn.setOnClickListener {
            val t = inputBox.text.toString().trim()
            if (t.isNotBlank()) {
                inputBox.setText("")
                toService(t, false)
            }
        }
        val micBtn = roundBtn("\uD83C\uDF99", 56, 24f)
        micBtn.setOnClickListener { toService(null, true) }
        inputRow.addView(inputBox, LinearLayout.LayoutParams(0, WRAP, 1f))
        inputRow.addView(sendBtn)
        inputRow.addView(micBtn)
        col.addView(inputRow)

        setContentView(frame)
        applyWall()

        val need = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS
        ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), 1)
    }

    override fun onBackPressed() {
        if (settingsScroll.visibility == View.VISIBLE) showSettings(false) else super.onBackPressed()
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
