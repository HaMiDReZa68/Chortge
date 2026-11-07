package com.example.smsaccounting

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
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

    /** فایل نسخهٔ جدید را با مرورگر گوشی باز می‌کند؛ دانلود و نصب با خود مرورگر و سیستم است */
    fun openDownload(ctx: Context, info: Info): Boolean = try {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(info.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (e: Exception) {
        false
    }
}
