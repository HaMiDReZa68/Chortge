package com.example.smsaccounting

/**
 * دسته‌بندی بر اساس کلمات کلیدی. ترتیب مهم است: دسته‌های دقیق‌تر اول می‌آیند.
 * کلمات کلیدی به شکل نرمال‌شده نوشته شده‌اند (نیم‌فاصله = فاصله، حروف انگلیسی کوچک).
 */
object Categorizer {
    const val OTHER = "سایر"
    const val INCOME = "درآمد"
    const val SALARY = "حقوق"

    private val rules: List<Pair<String, List<String>>> = listOf(
        "بنزین" to listOf(
            "بنزین", "جایگاه", "سوخت", "گازوئیل", "سی ان جی", "cng", "پمپ",
        ),
        "خوراک" to listOf(
            "رستوران", "کافه", "کافی شاپ", "فست فود", "سوپر", "هایپر", "فروشگاه زنجیره",
            "شهروند", "افق کوروش", "نانوایی", "قنادی", "شیرینی", "میوه", "تره بار", "گوشت",
            "مرغ", "کباب", "چلو", "پیتزا", "برگر", "ساندویچ", "بستنی", "آبمیوه", "کترینگ",
            "اسنپ فود", "snappfood", "snapp food", "دلینو", "delino", "ریحون",
            "food", "cafe", "restaurant", "pizza", "burger",
        ),
        "تفریح" to listOf(
            "سینما", "تئاتر", "کنسرت", "شهربازی", "پارک آبی", "گیم", "فیلیمو", "نماوا",
            "netflix", "spotify", "استخر", "باشگاه", "بولینگ", "هتل", "اقامتگاه", "کافه نت",
        ),
        "حمل‌ونقل" to listOf(
            "اسنپ", "snapp", "تپسی", "tapsi", "ماکسیم", "maxim", "مترو", "تاکسی", "اتوبوس",
            "بلیط", "قطار", "پرواز", "هواپیما", "علی بابا", "alibaba", "فلای تودی", "عوارض",
            "پارکینگ", "کرایه", "بی آر تی",
        ),
        "قبوض" to listOf(
            "قبض", "برق", "آب و فاضلاب", "شرکت آب", "شرکت گاز", "مخابرات", "تلفن ثابت",
            "شهرداری", "گاز",
        ),
        "شارژ و اینترنت" to listOf(
            "شارژ", "ایرانسل", "همراه اول", "رایتل", "شاتل", "های وب", "بسته اینترنت",
            "اینترنت همراه", "سیم کارت", "irancell", "rightel", "mci",
        ),
        "درمان" to listOf(
            "داروخانه", "دارو", "بیمارستان", "درمانگاه", "کلینیک", "دکتر", "دندان",
            "آزمایشگاه", "پزشک", "فیزیوتراپی", "اپتیک", "عینک", "رادیولوژی", "سونوگرافی",
            "pharmacy",
        ),
        "پوشاک" to listOf(
            "پوشاک", "لباس", "کفش", "بوتیک", "مانتو", "شلوار", "پیراهن", "جوراب", "مزون",
            "خیاطی", "lc waikiki", "ال سی وایکیکی",
        ),
        "آموزش" to listOf(
            "دانشگاه", "آموزشگاه", "کلاس", "کتاب", "شهریه", "دانشکده", "آکادمی", "مدرسه", "udemy",
        ),
        "خانه" to listOf(
            "اجاره", "رهن", "مبلمان", "لوازم خانگی", "دکوراسیون", "ابزار", "ساختمان", "کابینت",
        ),
        "بیمه" to listOf("بیمه"),
        "خرید اینترنتی" to listOf(
            "دیجی کالا", "دیجیکالا", "digikala", "باسلام", "torob", "دیوار", "okala", "اکالا",
            "بامیلو", "bamilo", "فروشگاه اینترنتی", "خرید اینترنتی", "درگاه", "زرین پال",
            "زرینپال", "آمازون", "aliexpress", "علی اکسپرس", "شیپور",
        ),
        "انتقال وجه" to listOf(
            "کارت به کارت", "انتقال وجه", "انتقال به", "انتقال از", "حواله",
        ),
    )

    val expenseCategories: List<String> = rules.map { it.first } + OTHER
    val incomeCategories: List<String> = listOf(SALARY, INCOME)

    /** کلید یادگیری برای فروشنده/پذیرنده */
    fun mkey(merchant: String): String = Norm.normalize(merchant).lowercase().take(24).trim()

    fun categorize(
        type: TxType,
        body: String,
        merchant: String,
        learned: Map<String, String>,
    ): String {
        val text = Norm.normalize(body).lowercase()
        if (type == TxType.INCOME) {
            return if (text.contains("حقوق") || text.contains("salary")) SALARY else INCOME
        }
        val key = mkey(merchant)
        if (key.isNotEmpty()) {
            val l = learned[key]
            if (l != null) return l
        }
        for ((cat, words) in rules) {
            if (words.any { text.contains(it) }) return cat
        }
        return OTHER
    }
}
