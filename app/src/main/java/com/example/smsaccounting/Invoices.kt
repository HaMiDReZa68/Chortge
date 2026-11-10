package com.example.smsaccounting

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class InvItem(val desc: String, val qty: Double, val unit: Long, val note: String) {
    val total: Long get() = Math.round(qty * unit)
}

data class Customer(val id: Long, val name: String, val phone: String, val address: String)

data class Invoice(
    val id: Long,
    val no: Long,
    val ts: Long,
    val custName: String,
    val custPhone: String,
    val custAddr: String,
    val items: List<InvItem>,
    val discMode: Int,      // 0 درصد، 1 مبلغ (ریال)
    val discVal: Double,
    val taxMode: Int,
    val taxVal: Double,
    val debt: Long,         // ریال
    val notes: String,
    val paid: Boolean,
)

class InvTotals(val sub: Long, val disc: Long, val tax: Long, val debt: Long, val total: Long)

fun Invoice.totals(): InvTotals {
    val sub = items.sumOf { it.total }
    val disc = (if (discMode == 0) Math.round(sub * discVal / 100.0) else Math.round(discVal)).coerceIn(0L, sub)
    val base = sub - disc
    val tax = (if (taxMode == 0) Math.round(base * taxVal / 100.0) else Math.round(taxVal)).coerceAtLeast(0L)
    return InvTotals(sub, disc, tax, debt, base + tax + debt)
}

fun itemsToJson(items: List<InvItem>): String {
    val a = JSONArray()
    for (i in items) a.put(JSONObject().put("d", i.desc).put("q", i.qty).put("u", i.unit).put("n", i.note))
    return a.toString()
}

fun itemsFromJson(s: String): List<InvItem> = try {
    val a = JSONArray(s)
    (0 until a.length()).map {
        val o = a.getJSONObject(it)
        InvItem(o.optString("d"), o.optDouble("q", 1.0), o.optLong("u"), o.optString("n"))
    }
} catch (e: Exception) {
    emptyList()
}

/** تنظیمات کلی فاکتور */
data class InvSettings(
    val name: String,
    val address: String,
    val phone: String,
    val next: Long,
    val toman: Boolean,
    val paper: String,   // A4 یا A5
    val footer: Boolean,
)

object InvFmt {
    fun money(rial: Long, toman: Boolean): String = Fa.money(if (toman) Math.round(rial / 10.0) else rial)
    fun unit(toman: Boolean) = if (toman) "تومان" else "ریال"
    fun num(d: Double): String =
        if (d % 1.0 == 0.0) Fa.digits(d.toLong().toString()) else Fa.digits(java.math.BigDecimal(d).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString())

    fun parse(s: String): Double =
        Norm.normalize(s).replace(",", "").replace('٫', '.').toDoubleOrNull() ?: 0.0
}

object InvImages {
    fun file(ctx: Context, kind: String) = File(ctx.filesDir, "inv_$kind.png")

    fun bitmap(ctx: Context, kind: String): Bitmap? {
        val f = file(ctx, kind)
        return if (f.exists()) BitmapFactory.decodeFile(f.path) else null
    }

    /** تصویر انتخاب‌شده را کوچک و به صورت PNG ذخیره می‌کند (شفافیت حفظ می‌شود) */
    fun save(ctx: Context, uri: Uri, kind: String): Boolean = try {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return false
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var s = 1
        while (o.outWidth / s > 1200 || o.outHeight / s > 1200) s *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s })
            ?: return false
        file(ctx, kind).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        true
    } catch (e: Exception) {
        false
    }

    fun delete(ctx: Context, kind: String) {
        file(ctx, kind).delete()
    }
}
