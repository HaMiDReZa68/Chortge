package com.example.smsaccounting

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

object Alarms {
    const val ACTION = "com.example.smsaccounting.OBLIGATION"

    private fun pi(ctx: Context, id: Long): PendingIntent {
        val i = Intent(ctx, ObligationReceiver::class.java).setAction(ACTION).putExtra("id", id)
        return PendingIntent.getBroadcast(
            ctx, 300_000 + (id % 100_000).toInt(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun schedule(ctx: Context, o: Obligation) {
        if (o.done || o.dueTs <= System.currentTimeMillis()) return
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, o.dueTs, pi(ctx, o.id))
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, o.dueTs, pi(ctx, o.id))
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, o.dueTs, pi(ctx, o.id))
        }
    }

    fun cancel(ctx: Context, id: Long) {
        (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi(ctx, id))
    }

    fun rescheduleAll(ctx: Context) {
        for (o in Db.get(ctx).obligations()) schedule(ctx, o)
    }
}

class ObligationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Alarms.ACTION) return
        val id = intent.getLongExtra("id", -1)
        val app = context.applicationContext
        val pending = goAsync()
        Thread {
            try {
                val o = Db.get(app).obligation(id)
                if (o != null && !o.done) Notifs.showObligation(app, o, Prefs(app).toman)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
