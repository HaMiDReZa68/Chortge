package com.example.smsaccounting

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/** پیامک جدید از سرشمارهٔ ثبت‌شده: در انتظار ثبت بابت و اعلان */
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
                    Importer.handleIncoming(context.applicationContext, sender, body, ts)
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
