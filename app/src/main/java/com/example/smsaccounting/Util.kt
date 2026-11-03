package com.example.smsaccounting

import java.security.MessageDigest
import java.util.Locale

/** نرمال‌سازی متن پیامک: ارقام فارسی/عربی به انگلیسی، یکسان‌سازی حروف، نیم‌فاصله به فاصله */
object Norm {
    private val spaces = Regex("[ \\t]+")

    /** شناسهٔ فرستنده برای مقایسه: برای شماره‌ها ۱۰ رقم آخر، برای نام‌ها حروف کوچک */
    fun sender(s: String): String {
        val t = s.trim().lowercase().replace(" ", "").replace("-", "")
        return if (t.isNotEmpty() && t.all { it.isDigit() || it == '+' }) {
            t.filter { it.isDigit() }.takeLast(10)
        } else {
            t
        }
    }

    fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            when (ch) {
                in '۰'..'۹' -> sb.append('0' + (ch - '۰'))
                in '٠'..'٩' -> sb.append('0' + (ch - '٠'))
                '٬', '،' -> sb.append(',')
                'ي' -> sb.append('ی')
                'ك' -> sb.append('ک')
                '‌' -> sb.append(' ')
                '‎', '‏', 'ـ', '\r' -> Unit
                else -> sb.append(ch)
            }
        }
        return spaces.replace(sb.toString(), " ").trim()
    }
}

/** نمایش اعداد با رقم فارسی */
object Fa {
    fun digits(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) sb.append(if (ch in '0'..'9') '۰' + (ch - '0') else ch)
        return sb.toString()
    }

    fun money(v: Long): String =
        digits(String.format(Locale.US, "%,d", v)).replace(',', '٬')
}

fun sha1(s: String): String =
    MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
        .joinToString("") { "%02x".format(it) }
