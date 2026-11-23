package com.decentnomi.assistant

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle

class PermActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val need = listOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS
        ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (need.isEmpty()) {
            finish()
        } else {
            requestPermissions(need.toTypedArray(), 1)
        }
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, g: IntArray) {
        super.onRequestPermissionsResult(rc, p, g)
        finish()
    }
}
