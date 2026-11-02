package com.example.smsaccounting

data class Parsed(
    val type: TxType,
    val amount: Long,      // ریال
    val balance: Long?,    // ریال
    val merchant: String,
)

/**
 * تشخیص تراکنش بانکی از متن پیامک. اگر پیامک تراکنش نباشد (تبلیغ، رمز پویا و ...) null برمی‌گرداند.
 * این منطق خالص کاتلین است و به اندروید وابسته نیست.
 */
object SmsParser {
    private val EXPENSE_WORDS = listOf(
        "برداشت", "خرید", "پرداخت", "کسر", "انتقال به",
        "withdraw", "purchase", "debit", "payment",
    )
    private val INCOME_WORDS = listOf(
        "واریز", "دریافت", "افزایش", "حقوق",
        "deposit", "credit",
    )

    private val OTP = Regex("""رمز|otp|cvv2?|کد تایید|کد فعال|کد ورود|کد امنیتی|\bpin\b|پویا""")
    private val BALANCE_WORD = Regex("""مانده|موجودی|balance""")

    private val BALANCE = Regex(
        """(?:مانده|موجودی)(?:\s*(?:حساب|کل|قابل\s*برداشت|شما))?\s*[:：]?\s*(?:ریال|تومان)?\s*[+\-−]?\s*(\d[\d,]*)[+\-−]?"""
    )

    private val DATE = Regex("""\b\d{2,4}[/\-.]\d{1,2}[/\-.]\d{1,2}\b""")
    private val SHORT_DATE = Regex("""\b\d{1,2}/\d{1,2}\b""")
    private val TIME = Regex("""\b\d{1,2}:\d{2}(?::\d{2})?\b""")
    private val MASKED = Regex("""[\d\-]*[*#•]+[\d*#•\-]*|\b[xX]{2,}\d*""")
    private val CARD = Regex("""\b\d{4}[ \-]\d{4}[ \-]\d{4}[ \-]\d{4}\b""")
    private val LONG_ID = Regex("""\b\d{10,}\b""")

    private val SIGN_LEAD = Regex("""(?:^|[\s:：(])([+\-−])(\d[\d,]{2,})""")
    private val SIGN_TRAIL = Regex("""(\d[\d,]{2,})([+\-−])(?=\s|$|\))""")

    private val KW_AMOUNT = Regex(
        """(?:مبلغ|برداشت|واریز|خرید|پرداخت|دریافت|کسر|افزایش|amount|amt)\s*[:：]?\s*(?:ریال|تومان)?\s*[:：]?\s*(\d[\d,]*)""",
        RegexOption.IGNORE_CASE,
    )
    private val FALLBACK_NUM = Regex("""\d[\d,]{3,}""")

    private val TOMAN = Regex("""تومان|تومن""")
    private val RIAL = Regex("""ریال""")

    private val MERCHANT = listOf(
        Regex("""(?:پذیرنده|فروشگاه|فروشنده|شرح|بابت|مقصد)\s*[:：\-]?\s*([^\n\d:]{2,30})"""),
        Regex("""خرید از\s*([^\n\d:]{2,30})"""),
    )
    private val MERCHANT_TAIL = Regex("""\s*(?:تاریخ|ساعت|مبلغ|مانده|موجودی|کارت|شماره).*$""")

    private fun toLong(s: String): Long? = s.replace(",", "").toLongOrNull()

    private fun detectType(s: String): TxType? {
        val e = EXPENSE_WORDS.map { s.indexOf(it) }.filter { it >= 0 }.minOrNull()
        val i = INCOME_WORDS.map { s.indexOf(it) }.filter { it >= 0 }.minOrNull()
        if (e == null && i == null) return null
        if (e == null) return TxType.INCOME
        if (i == null) return TxType.EXPENSE
        return if (e < i) TxType.EXPENSE else TxType.INCOME
    }

    private fun cleanMerchant(s: String): String =
        MERCHANT_TAIL.replace(s.replace(Regex("""\s+"""), " ").trim(), "")
            .trim(':', '-', '.', ' ')

    fun parse(raw: String): Parsed? {
        val text = Norm.normalize(raw)
        if (text.length < 8) return null
        val lower = text.lowercase()
        if (OTP.containsMatchIn(lower) && !BALANCE_WORD.containsMatchIn(lower)) return null

        // مانده حساب را جدا می‌کنیم تا با مبلغ تراکنش اشتباه گرفته نشود
        var work = text
        var balance: Long? = null
        val bm = BALANCE.find(work)
        if (bm != null) {
            balance = toLong(bm.groupValues[1])
            work = work.removeRange(bm.range).toString()
        }
        val typeText = work.lowercase()

        // حذف تاریخ، ساعت، شماره کارت و شناسه‌های بلند
        for (p in listOf(DATE, SHORT_DATE, TIME, MASKED, CARD, LONG_ID)) {
            work = p.replace(work, " ")
        }

        var amount: Long? = null
        var type: TxType? = null

        // ۱) عدد علامت‌دار مثل -1,000,000 یا 1,000,000+
        val lead = SIGN_LEAD.find(work)
        val trail = SIGN_TRAIL.find(work)
        val signed = listOfNotNull(lead, trail).minByOrNull { it.range.first }
        if (signed != null) {
            val isLead = signed === lead
            val num = toLong(signed.groupValues[if (isLead) 2 else 1])
            val sign = signed.groupValues[if (isLead) 1 else 2]
            if (num != null && num >= 1000) {
                amount = num
                type = if (sign == "+") TxType.INCOME else TxType.EXPENSE
            }
        }
        // ۲) عدد بعد از کلمه‌ای مثل «مبلغ» یا «برداشت»
        if (amount == null) {
            amount = KW_AMOUNT.find(work)?.let { toLong(it.groupValues[1]) }?.takeIf { it >= 1000 }
        }
        // ۳) اولین عدد معقول
        if (amount == null) {
            amount = FALLBACK_NUM.findAll(work).mapNotNull { toLong(it.value) }.firstOrNull { it >= 1000 }
        }
        val finalAmount = amount ?: return null
        val finalType = type ?: detectType(typeText) ?: return null

        val isToman = TOMAN.containsMatchIn(text) && !RIAL.containsMatchIn(text)
        val factor = if (isToman) 10L else 1L

        var merchant = ""
        for (r in MERCHANT) {
            val m = r.find(text) ?: continue
            val c = cleanMerchant(m.groupValues[1])
            if (c.length >= 2) {
                merchant = c
                break
            }
        }

        return Parsed(finalType, finalAmount * factor, balance?.let { it * factor }, merchant)
    }
}
