package com.example.smsaccounting

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object Share {
    fun write(ctx: Context, uri: Uri, bytes: ByteArray): Boolean = try {
        ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } != null
    } catch (e: Exception) {
        false
    }

    fun read(ctx: Context, uri: Uri): ByteArray? = try {
        ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (e: Exception) {
        null
    }

    /** فایل را در پوشهٔ موقت می‌نویسد و منوی اشتراک‌گذاری (گوگل درایو، دراپ‌باکس، تلگرام و ...) را باز می‌کند */
    fun send(ctx: Context, name: String, mime: String, bytes: ByteArray) {
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val f = File(dir, name)
        f.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        val i = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, "ارسال یا ذخیره").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
