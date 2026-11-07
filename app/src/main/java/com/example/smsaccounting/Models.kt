package com.example.smsaccounting

enum class TxType {
    INCOME, EXPENSE,

    /** انتقال بین حساب‌های خودم؛ دخل و خرج حساب نمی‌شود */
    TRANSFER_OUT, TRANSFER_IN;

    val isTransfer get() = this == TRANSFER_OUT || this == TRANSFER_IN
}

/** مبلغ‌ها همیشه به ریال ذخیره می‌شوند */
data class Tx(
    val id: Long = 0,
    val hash: String,
    val sender: String,
    val type: TxType,
    val amount: Long,
    val balance: Long?,
    val merchant: String,
    val mkey: String,
    val category: String,
    val ts: Long,
    val body: String,
    val accountId: Long = 0,
    val sub: String = "",
    val note: String = "",
)

/** حساب بانکی یا «نقدی» (kind = CASH، شناسهٔ ۱) */
data class Account(
    val id: Long,
    val name: String,
    val bank: String,
    val kind: String,
    val lastBalance: Long?,
    val lastBalanceTs: Long,
) {
    val isCash: Boolean get() = kind == "CASH"
}

/** سرشمارهٔ پیامک که به یک حساب وصل است؛ match اختیاری است (مثلاً ۴ رقم آخر کارت) */
data class SmsSource(val id: Long, val sender: String, val accountId: Long, val match: String)

/** پیامک تراکنشی که منتظر ثبت بابت و توضیحات توسط کاربر است */
data class PendingTx(
    val id: Long,
    val hash: String,
    val accountId: Long,
    val sender: String,
    val type: TxType,
    val amount: Long,
    val balance: Long?,
    val merchant: String,
    val category: String,
    val sub: String,
    val ts: Long,
    val body: String,
)

data class Category(
    val id: Long,
    val name: String,
    val type: TxType,
    val parentId: Long,
    val icon: String,
    val keywords: String,
)

enum class ObKind(val label: String, val icon: String) {
    CHEQUE("چک", "🧾"),
    CREDIT("طلب", "📥"),
    DEBT("بدهی", "📤"),
    INSTALLMENT("قسط", "📆"),
    LOAN("وام", "🏦"),
}

data class Obligation(
    val id: Long,
    val kind: ObKind,
    val title: String,
    val party: String,
    val amount: Long,
    val dueTs: Long,
    val note: String,
    val done: Boolean,
    val repeat: Boolean,
    val remaining: Int,
    val created: Long,
    val smsTo: String = "",
    val smsText: String = "",
    val smsSent: Boolean = false,
)

data class SenderCandidate(val sender: String, val count: Int, val lastTs: Long, val sample: String)

/** حدس نام بانک از روی شناسهٔ فرستندهٔ پیامک؛ اگر نشناسیم همان شناسه نمایش داده می‌شود */
object BankNames {
    private val known = listOf(
        "melli" to "بانک ملی", "mellat" to "بانک ملت", "saman" to "بانک سامان",
        "pasargad" to "بانک پاسارگاد", "sepah" to "بانک سپه", "tejarat" to "بانک تجارت",
        "refah" to "بانک رفاه", "keshavarzi" to "بانک کشاورزی", "maskan" to "بانک مسکن",
        "saderat" to "بانک صادرات", "parsian" to "بانک پارسیان", "ayandeh" to "بانک آینده",
        "shahr" to "بانک شهر", "dey" to "بانک دی", "blu" to "بلو بانک", "sina" to "بانک سینا",
        "ansar" to "بانک انصار", "eghtesad" to "بانک اقتصاد نوین", "karafarin" to "بانک کارآفرین",
        "resalat" to "بانک قرض‌الحسنه رسالت", "mehr" to "بانک مهر ایران", "post" to "پست بانک",
        "sarmayeh" to "بانک سرمایه", "iranzamin" to "بانک ایران زمین", "tourism" to "بانک گردشگری",
        "ملی" to "بانک ملی", "ملت" to "بانک ملت", "سامان" to "بانک سامان", "پاسارگاد" to "بانک پاسارگاد",
        "سپه" to "بانک سپه", "تجارت" to "بانک تجارت", "رفاه" to "بانک رفاه", "کشاورزی" to "بانک کشاورزی",
        "مسکن" to "بانک مسکن", "صادرات" to "بانک صادرات", "پارسیان" to "بانک پارسیان",
        "آینده" to "بانک آینده", "شهر" to "بانک شهر", "سینا" to "بانک سینا", "انصار" to "بانک انصار",
    )

    fun guess(sender: String): String {
        val s = sender.lowercase()
        return known.firstOrNull { s.contains(it.first) }?.second ?: sender
    }
}
