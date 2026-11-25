package com.decentnomi.assistant

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

object Offline {
    private const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip"
    private const val NAME = "vosk-model-small-hi-0.22"

    fun dir(ctx: Context): File = File(ctx.filesDir, NAME)

    fun ready(ctx: Context): Boolean =
        File(dir(ctx), "am").isDirectory && File(dir(ctx), "conf").isDirectory

    fun download(ctx: Context, prog: (Int) -> Unit): String? {
        return try {
            val zip = File(ctx.cacheDir, "m.zip")
            val c = URL(MODEL_URL).openConnection() as HttpURLConnection
            c.connectTimeout = 10000
            c.readTimeout = 30000
            val total = c.contentLength
            var got = 0L
            var last = -1
            c.inputStream.use { inp ->
                zip.outputStream().use { out ->
                    val buf = ByteArray(65536)
                    while (true) {
                        val n = inp.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        got += n
                        if (total > 0) {
                            val p = (got * 100 / total).toInt()
                            if (p / 20 != last / 20) {
                                last = p
                                prog(p)
                            }
                        }
                    }
                }
            }
            val root = ctx.filesDir
            val rootPath = root.canonicalPath
            ZipInputStream(zip.inputStream()).use { zin ->
                while (true) {
                    val e = zin.nextEntry ?: break
                    val f = File(root, e.name)
                    if (!f.canonicalPath.startsWith(rootPath)) continue
                    if (e.isDirectory) {
                        f.mkdirs()
                    } else {
                        f.parentFile?.mkdirs()
                        f.outputStream().use { zin.copyTo(it) }
                    }
                }
            }
            zip.delete()
            if (ready(ctx)) null else "Model ki files nahi mili"
        } catch (e: Exception) {
            "Download ERR " + e.message
        }
    }
}
