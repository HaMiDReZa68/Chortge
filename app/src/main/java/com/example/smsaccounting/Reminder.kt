package com.example.smsaccounting

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.util.Calendar

object Reminder {
    private const val CHANNEL = "daily_reminder"
    private const val REQ = 4201
    const val ACTION = "com.example.smsaccounting.REMINDER"

    private fun pending(ctx: Context): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).setAction(ACTION)
        return PendingIntent.getBroadcast(
            ctx, REQ, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun nextTrigger(h: Int, m: Int, now: Long = System.currentTimeMillis()): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = now
        c.set(Calendar.HOUR_OF_DAY, h)
        c.set(Calendar.MINUTE, m)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        if (c.timeInMillis <= now + 1000) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    fun schedule(ctx: Context) {
        val p = Prefs(ctx)
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val at = nextTrigger(p.reminderH, p.reminderM)
        val pi = pending(ctx)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (exact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(ctx))
    }

    fun show(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val prefs = Prefs(ctx)
        val dis = prefs.disabledSenders()
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        val start = c.timeInMillis
        val spent = Db.get(ctx).query(start, start + 86_400_000L)
            .filter { it.sender !in dis && it.type == TxType.EXPENSE }
            .sumOf { it.amount }

        val unit = if (prefs.toman) "تومان" else "ریال"
        val text = if (spent > 0) {
            "امروز " + Fa.money(if (prefs.toman) spent / 10 else spent) + " " + unit +
                " خرج کرده‌ای. تراکنش‌ها را بررسی و دسته‌بندی کن."
        } else {
            "امروز هنوز هزینه‌ای ثبت نشده. اگر خرج نقدی داشتی، ثبتش کن."
        }

        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "یادآوری روزانه", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_wallet)
            .setContentTitle("حسابداری پیامک")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(1, n)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Reminder.ACTION) return
        val app = context.applicationContext
        val pending = goAsync()
        Thread {
            try {
                if (Prefs(app).reminderOn) {
                    Reminder.show(app)
                    Reminder.schedule(app)
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}

/** بعد از روشن‌شدن گوشی یا به‌روزرسانی اپ، یادآوری دوباره زمان‌بندی می‌شود */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val a = intent.action
        if (a != Intent.ACTION_BOOT_COMPLETED && a != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext
        if (Prefs(app).reminderOn) Reminder.schedule(app)
    }
}
