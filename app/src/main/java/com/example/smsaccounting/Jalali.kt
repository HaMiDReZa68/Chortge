package com.example.smsaccounting

import android.icu.util.Calendar
import android.icu.util.ULocale

/** کار با ماه شمسی (گزارش‌ها بر اساس ماه شمسی هستند) */
object Jalali {
    private fun cal(): Calendar = Calendar.getInstance(ULocale("fa_IR@calendar=persian"))

    val monthNames = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
    )

    data class YM(val year: Int, val month: Int) {
        fun next() = if (month == 12) YM(year + 1, 1) else YM(year, month + 1)
        fun prev() = if (month == 1) YM(year - 1, 12) else YM(year, month - 1)
    }

    fun ymOf(ms: Long): YM {
        val c = cal()
        c.timeInMillis = ms
        return YM(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
    }

    private fun startOf(ym: YM): Long {
        val c = cal()
        c.clear()
        c.set(ym.year, ym.month - 1, 1, 0, 0, 0)
        return c.timeInMillis
    }

    /** بازهٔ زمانی ماه: [شروع، شروع ماه بعد) */
    fun range(ym: YM): Pair<Long, Long> = startOf(ym) to startOf(ym.next())

    fun title(ym: YM): String = "${monthNames[ym.month - 1]} ${Fa.digits(ym.year.toString())}"

    fun dayTime(ms: Long): String {
        val c = cal()
        c.timeInMillis = ms
        val day = c.get(Calendar.DAY_OF_MONTH)
        val month = monthNames[c.get(Calendar.MONTH)]
        val hh = c.get(Calendar.HOUR_OF_DAY)
        val mm = c.get(Calendar.MINUTE)
        return Fa.digits("$day $month") + " – " + Fa.digits("%02d:%02d".format(hh, mm))
    }

    fun dayLabel(ms: Long): String {
        val c = cal()
        c.timeInMillis = ms
        return Fa.digits("${c.get(Calendar.DAY_OF_MONTH)} ${monthNames[c.get(Calendar.MONTH)]}")
    }

    fun time(ms: Long): String {
        val c = cal()
        c.timeInMillis = ms
        return Fa.digits("%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)))
    }

    fun daysIn(ym: YM): Int {
        val (a, b) = range(ym)
        return Math.round((b - a) / 86_400_000.0).toInt()
    }

    fun dayOf(ms: Long): Int {
        val c = cal()
        c.timeInMillis = ms
        return c.get(Calendar.DAY_OF_MONTH)
    }

    fun full(ms: Long): String {
        val c = cal()
        c.timeInMillis = ms
        return Fa.digits(
            "${c.get(Calendar.DAY_OF_MONTH)} ${monthNames[c.get(Calendar.MONTH)]} ${c.get(Calendar.YEAR)}"
        )
    }
}
