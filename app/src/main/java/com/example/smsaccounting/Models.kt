package com.example.smsaccounting

enum class TxType { INCOME, EXPENSE }

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
)

data class BankInfo(
    val sender: String,
    val name: String,
    val count: Int,
    val enabled: Boolean,
    val balance: Long? = null,
)

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
