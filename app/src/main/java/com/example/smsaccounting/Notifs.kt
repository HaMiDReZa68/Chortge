package com.example.smsaccounting

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

object Notifs {
    const val CH_DAILY = "daily_reminder"
    const val CH_TX = "new_tx"
    const val CH_OB = "obligation"

    fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun channels(ctx: Context): NotificationManager {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_DAILY, "یادآوری روزانه", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_TX, "تراکنش جدید", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_OB, "سررسیدها", NotificationManager.IMPORTANCE_HIGH))
        return nm
    }

    /** page: "pending" یا "obligations" یا خالی */
    fun open(ctx: Context, page: String, req: Int): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("open", page)
        return PendingIntent.getActivity(ctx, req, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun post(ctx: Context, id: Int, ch: String, title: String, text: String, page: String) {
        if (!canPost(ctx)) return
        val nm = channels(ctx)
        val n = Notification.Builder(ctx, ch)
            .setSmallIcon(R.drawable.ic_stat_wallet)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open(ctx, page, id))
            .setAutoCancel(true)
            .build()
        nm.notify(id, n)
    }

    fun showNewTx(ctx: Context, pendingId: Long, account: String, type: TxType, amountRial: Long, toman: Boolean) {
        val v = if (toman) amountRial / 10 else amountRial
        val unit = if (toman) "تومان" else "ریال"
        val what = if (type == TxType.EXPENSE) "برداشت" else "واریز"
        post(
            ctx, 100_000 + (pendingId % 50_000).toInt(), CH_TX,
            "$what ${Fa.money(v)} $unit" + if (account.isNotBlank()) " · $account" else "",
            "بابت این تراکنش را ثبت کن.", "pending",
        )
    }

    fun showObligation(ctx: Context, o: Obligation, toman: Boolean) {
        val v = if (toman) o.amount / 10 else o.amount
        val unit = if (toman) "تومان" else "ریال"
        val amt = if (o.amount > 0) " · " + Fa.money(v) + " " + unit else ""
        val party = if (o.party.isNotBlank()) " (${o.party})" else ""
        post(ctx, 200_000 + (o.id % 50_000).toInt(), CH_OB, "${o.kind.label}: ${o.title}$party", "سررسید رسیده$amt", "obligations")
    }
}
