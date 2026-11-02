package com.example.smsaccounting

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/** هر پیامک جدید که برسد، اگر تراکنش بانکی باشد خودکار ثبت می‌شود */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return

        val ts = System.currentTimeMillis()
        val grouped = parts.groupBy { it.originatingAddress ?: "" }
        val pending = goAsync()
        Thread {
            try {
                for ((sender, msgs) in grouped) {
                    val body = msgs.joinToString("") { it.messageBody ?: "" }
                    Importer.handle(context.applicationContext, sender, body, ts)
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
