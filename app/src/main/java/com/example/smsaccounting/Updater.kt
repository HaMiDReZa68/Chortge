package com.example.smsaccounting

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** به‌روزرسانی از آخرین Release گیت‌هاب (ریپوی عمومی) */
object Updater {
    private const val REPO = "HaMiDReZa68/Chortge"

    class Info(val code: Long, val name: String, val url: String)

    fun currentCode(ctx: Context): Long =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Chortge-Updater")
        }

    /** نسخهٔ جدید را برمی‌گرداند؛ null یعنی نسخهٔ جدیدتری نیست. در خطای شبکه استثنا می‌دهد */
    fun check(ctx: Context): Info? {
        val c = open("https://api.github.com/repos/$REPO/releases/latest")
        val text = try {
            if (c.responseCode != 200) throw IllegalStateException("http ${c.responseCode}")
            c.inputStream.bufferedReader().readText()
        } finally {
            c.disconnect()
        }
        val j = JSONObject(text)
        val tag = j.getString("tag_name")
        val n = tag.filter { it.isDigit() }.toLongOrNull() ?: return null
        val code = n + 100 // همان فرمول versionCode در build.gradle
        val assets = j.getJSONArray("assets")
        var url: String? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.getString("name").endsWith(".apk")) url = a.getString("browser_download_url")
        }
        if (url == null || code <= currentCode(ctx)) return null
        return Info(code, tag, url)
    }

    fun download(ctx: Context, info: Info): File {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        val f = File(dir, "update.apk")
        val c = open(info.url)
        try {
            c.inputStream.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        } finally {
            c.disconnect()
        }
        return f
    }

    /** false یعنی اول باید اجازهٔ «نصب از این اپ» داده شود (صفحهٔ تنظیمات باز شد) */
    fun install(ctx: Context, f: File): Boolean {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }
}
