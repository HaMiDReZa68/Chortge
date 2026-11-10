package com.example.smsaccounting

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import java.io.ByteArrayOutputStream
import java.io.File

object InvoicePdf {
    private const val W = 595f
    private const val H = 842f
    private const val M = 30f
    private val OLIVE = Color.parseColor("#5F5F00")
    private val BEIGE = Color.parseColor("#E6E3D0")
    private val CREAM = Color.parseColor("#FDF6EE")
    private val GRAY = Color.parseColor("#C9C9C9")
    private val INK = Color.parseColor("#222222")
    private val MUTED = Color.parseColor("#666666")
    private val RED = Color.parseColor("#C03030")

    private fun rl(s: String) = "‏$s"

    /** paper: "A4" یا "A5" */
    fun build(ctx: Context, inv: Invoice, set: InvSettings, paper: String): ByteArray {
        val tf = ResourcesCompat.getFont(ctx, R.font.vazirmatn_regular) ?: Typeface.DEFAULT
        val tfb = ResourcesCompat.getFont(ctx, R.font.vazirmatn_bold) ?: Typeface.DEFAULT_BOLD
        val a5 = paper == "A5"
        val pw = if (a5) 420 else 595
        val ph = if (a5) 595 else 842
        val sc = pw / W
        val toman = set.toman
        val tot = inv.totals()
        val unit = InvFmt.unit(toman)
        fun m(r: Long) = InvFmt.money(r, toman)

        fun paint(size: Float, bold: Boolean = false, color: Int = INK) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = if (bold) tfb else tf; textSize = size; this.color = color
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = GRAY }

        val doc = PdfDocument()
        var pageNo = 0
        lateinit var page: PdfDocument.Page
        lateinit var c: Canvas
        fun start() {
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, pageNo).create())
            c = page.canvas
            c.scale(sc, sc)
        }
        fun finish() = doc.finishPage(page)

        fun txR(s: String, x: Float, y: Float, p: Paint) { p.textAlign = Paint.Align.RIGHT; c.drawText(rl(s), x, y, p) }
        fun txL(s: String, x: Float, y: Float, p: Paint) { p.textAlign = Paint.Align.LEFT; c.drawText(rl(s), x, y, p) }
        fun txC(s: String, x: Float, y: Float, p: Paint) { p.textAlign = Paint.Align.CENTER; c.drawText(rl(s), x, y, p) }
        fun fit(s: String, p: Paint, maxW: Float): String {
            var r = s
            if (p.measureText(r) <= maxW) return r
            while (r.length > 1 && p.measureText("$r…") > maxW) r = r.dropLast(1)
            return "$r…"
        }
        fun lay(s: String, p: Paint, w: Float): StaticLayout {
            val t = TextPaint(p).apply { textAlign = Paint.Align.LEFT }
            return StaticLayout.Builder.obtain(s, 0, s.length, t, w.toInt().coerceAtLeast(10))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .setLineSpacing(0f, 1.1f)
                .build()
        }
        fun drawLay(l: StaticLayout, x: Float, y: Float) { c.save(); c.translate(x, y); l.draw(c); c.restore() }
        fun rrect(l: Float, t: Float, r: Float, b: Float, rad: Float, p: Paint) = c.drawRoundRect(RectF(l, t, r, b), rad, rad, p)

        val logo = InvImages.bitmap(ctx, "logo")
        val stamp = InvImages.bitmap(ctx, "stamp")
        val R = W - M
        val L = M

        start()
        var y = M

        // ---------------- سربرگ
        run {
            val top = y
            val hh = 128f
            fill.color = BEIGE
            rrect(L, top, R, top + hh, 14f, fill)
            c.save()
            val clip = Path().apply { addRoundRect(RectF(L, top, R, top + hh), 14f, 14f, Path.Direction.CW) }
            c.clipPath(clip)
            fill.color = OLIVE
            c.drawRect(L, top, R, top + 7f, fill)
            c.restore()
            rrect(L, top, R, top + hh, 14f, stroke)
            txC("فاکتور فروش", W / 2, top + 38f, paint(21f))
            stroke.color = GRAY
            c.drawLine(L + 12, top + 48f, R - 12, top + 48f, stroke)

            val cx = R - 14f - 33f
            val cy = top + 88f
            if (logo != null) {
                c.save()
                c.clipPath(Path().apply { addCircle(cx, cy, 33f, Path.Direction.CW) })
                val sz = minOf(logo.width, logo.height)
                val src = android.graphics.Rect((logo.width - sz) / 2, (logo.height - sz) / 2, (logo.width + sz) / 2, (logo.height + sz) / 2)
                c.drawBitmap(logo, src, RectF(cx - 33, cy - 33, cx + 33, cy + 33), Paint(Paint.FILTER_BITMAP_FLAG))
                c.restore()
            } else {
                fill.color = Color.parseColor("#FFF0C2")
                c.drawCircle(cx, cy, 33f, fill)
                txC(set.name.trim().take(1), cx, cy + 9f, paint(24f, true, OLIVE))
            }
            stroke.color = OLIVE; stroke.strokeWidth = 2f
            c.drawCircle(cx, cy, 33f, stroke)
            stroke.color = GRAY; stroke.strokeWidth = 1f

            val nameR = cx - 33f - 12f
            txR(set.name.ifBlank { "—" }, nameR, top + 72f, paint(15f, true))
            val info = paint(9.5f, color = Color.parseColor("#333333"))
            txL("تاریخ: " + Fa.digits(Jalali.full(inv.ts)), L + 14f, top + 72f, info)
            txL("شمارهٔ فاکتور: " + Fa.digits(inv.no.toString()), L + 14f, top + 96f, info)
            if (set.address.isNotBlank()) txR("آدرس: " + fit(Fa.digits(set.address), info, nameR - (L + 150f)), nameR, top + 96f, info)
            if (set.phone.isNotBlank()) txR("تلفن: " + Fa.digits(set.phone), nameR, top + 114f, info)
            y = top + hh + 10f
        }

        // ---------------- مشتری
        run {
            fill.color = CREAM
            c.drawRect(0f, y, W, y + 38f, fill)
            val cy = y + 19f
            stroke.color = OLIVE; stroke.strokeWidth = 1.4f
            c.drawCircle(R - 12f, cy, 11f, stroke)
            c.drawCircle(R - 12f, cy - 3f, 3.2f, stroke)
            c.drawArc(RectF(R - 18.5f, cy + 1f, R - 5.5f, cy + 12f), 200f, 140f, false, stroke)
            stroke.color = GRAY; stroke.strokeWidth = 1f
            var x = R - 32f
            val lb = paint(11f, true, OLIVE)
            txR("مشتری", x, cy + 4f, lb)
            x -= lb.measureText("مشتری") + 16f
            val p = paint(10f)
            if (inv.custName.isNotBlank()) {
                val s = fit(inv.custName, p, 150f); txR(s, x, cy + 4f, p); x -= p.measureText(s) + 20f
            }
            if (inv.custPhone.isNotBlank()) {
                val s = "تلفن: " + Fa.digits(inv.custPhone); txR(s, x, cy + 4f, p); x -= p.measureText(s) + 20f
            }
            if (inv.custAddr.isNotBlank() && x - L > 40f) txR(fit(Fa.digits(inv.custAddr), p, x - L - 6f), x, cy + 4f, p)
            y += 38f + 18f
        }

        // ---------------- جدول
        val tw = R - L
        val fr = floatArrayOf(0.36f, 0.17f, 0.10f, 0.17f, 0.20f)
        val heads = listOf("اقلام", "فی ($unit)", "مقدار", "قیمت کل ($unit)", "توضیحات")
        val colR = FloatArray(5); val colL = FloatArray(5)
        var acc = R
        for (i in 0..4) { colR[i] = acc; acc -= tw * fr[i]; colL[i] = acc }
        var segTop = y
        val limit = H - M - 8f

        fun tableHeader() {
            fill.color = BEIGE
            c.drawRect(L, y, R, y + 26f, fill)
            val hp = paint(9f, true)
            for (i in 0..4) {
                if (i == 0) txR(heads[i], colR[i] - 8f, y + 17f, hp) else txC(heads[i], (colR[i] + colL[i]) / 2, y + 17f, hp)
            }
            c.drawLine(L, y + 26f, R, y + 26f, stroke)
            for (i in 0..3) c.drawLine(colL[i], y, colL[i], y + 26f, stroke)
            y += 26f
        }
        fun closeSegment() {
            rrect(L, segTop, R, y, 12f, stroke)
        }
        tableHeader()

        val body = paint(9.5f)
        val small = paint(8f, color = MUTED)
        inv.items.forEachIndexed { idx, it ->
            val nameL = lay(Fa.digits("${idx + 1}. " + it.desc), body, colR[0] - colL[0] - 14f)
            val noteL = lay(if (it.note.isBlank()) "-" else Fa.digits(it.note), small, colR[4] - colL[4] - 10f)
            val rh = maxOf(28f, nameL.height + 14f, noteL.height + 14f)
            if (y + rh > limit) {
                closeSegment(); finish(); start(); y = M; segTop = y; tableHeader()
            }
            drawLay(nameL, colL[0] + 6f, y + (rh - nameL.height) / 2)
            val mid = y + rh / 2 + 3.5f
            txC(m(it.unit), (colR[1] + colL[1]) / 2, mid, body)
            txC(InvFmt.num(it.qty), (colR[2] + colL[2]) / 2, mid, body)
            txC(m(it.total), (colR[3] + colL[3]) / 2, mid, body)
            drawLay(noteL, colL[4] + 5f, y + (rh - noteL.height) / 2)
            c.drawLine(L, y + rh, R, y + rh, stroke)
            for (i in 0..3) c.drawLine(colL[i], y, colL[i], y + rh, stroke)
            y += rh
        }

        // ---------------- جمع‌ها و توضیحات
        val rowsT = ArrayList<Triple<String, String, Int>>() // label, value, kind(0 عادی 1 تخفیف)
        rowsT.add(Triple("جمع مبالغ", m(tot.sub) + " $unit", 0))
        if (tot.disc > 0) rowsT.add(Triple("تخفیف" + if (inv.discMode == 0) " (" + InvFmt.num(inv.discVal) + "٪)" else "", m(tot.disc) + " $unit", 1))
        if (tot.tax > 0) rowsT.add(Triple("مالیات" + if (inv.taxMode == 0) " (" + InvFmt.num(inv.taxVal) + "٪)" else "", m(tot.tax) + " $unit", 0))
        if (tot.debt > 0) rowsT.add(Triple("بدهی قبلی", m(tot.debt) + " $unit", 0))
        val rowH = 26f
        val finalH = 34f
        val totW = tw * 0.60f
        val notesW = tw - totW
        val notesL = lay(if (inv.notes.isBlank()) "" else Fa.digits(inv.notes), paint(8.5f, color = MUTED), notesW - 22f)
        val blockH = maxOf(rowsT.size * rowH + finalH, 34f + notesL.height + 12f)
        if (y + blockH > limit) {
            closeSegment(); finish(); start(); y = M; segTop = y
        }
        run {
            val bt = y
            val bb = y + blockH
            // کادر توضیحات (راست)
            val nx = R - notesW
            c.drawLine(nx, bt, nx, bb, stroke)
            txR("توضیحات", R - 12f, bt + 20f, paint(10.5f, true, OLIVE))
            if (inv.notes.isNotBlank()) drawLay(notesL, nx + 11f, bt + 28f)
            // جمع‌ها (چپ)
            var ry = bt
            val lp = paint(10f)
            for ((lbl, v, k) in rowsT) {
                txR(lbl, nx - 12f, ry + 17f, lp)
                txL(v, L + 12f, ry + 17f, paint(10f, true, if (k == 1) RED else INK))
                ry += rowH
            }
            val fy = bb - finalH
            fill.color = BEIGE
            c.drawRect(L, fy, nx, bb, fill)
            stroke.color = OLIVE; stroke.strokeWidth = 1.8f
            c.drawRect(L + 1f, fy, nx, bb - 1f, stroke)
            stroke.color = GRAY; stroke.strokeWidth = 1f
            txR("مبلغ قابل پرداخت", nx - 12f, fy + 21f, paint(11f, true))
            txL(m(tot.total) + " $unit", L + 12f, fy + 21f, paint(11f, true))
            y = bb
            closeSegment()
        }

        // ---------------- پانویس و امضا
        y += 16f
        if (set.footer) {
            val fp = paint(8.5f, color = MUTED)
            val fl = lay("فاکتور با اپلیکیشن «هومایه» ایجاد شده است. مسئولیت‌های مربوط به معامله و محتوای فاکتور بر عهدهٔ فروشنده است.", fp, tw - 36f)
            val fh = fl.height + 22f
            if (y + fh + 100f > limit) { finish(); start(); y = M }
            rrect(L, y, R, y + fh, 14f, stroke)
            drawLay(fl, L + 18f, y + 11f)
            y += fh + 16f
        }
        if (y + 96f > limit) { finish(); start(); y = M }
        val sp = paint(9.5f, true, OLIVE)
        val buyerX = R - 80f
        val sellerX = L + 100f
        if (stamp != null) {
            val maxW = 110f; val maxH = 62f
            val r = minOf(maxW / stamp.width, maxH / stamp.height)
            val w = stamp.width * r; val h = stamp.height * r
            c.drawBitmap(stamp, null, RectF(sellerX - w / 2, y, sellerX + w / 2, y + h), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        txC("امضا خریدار", buyerX, y + 78f, sp)
        txC("مهر و امضا فروشنده", sellerX, y + 78f, sp)

        finish()
        val bos = ByteArrayOutputStream()
        doc.writeTo(bos)
        doc.close()
        return bos.toByteArray()
    }

    /** برای پیش‌نمایش داخل برنامه */
    fun previews(ctx: Context, bytes: ByteArray, widthPx: Int): List<Bitmap> {
        val f = File(ctx.cacheDir, "preview.pdf")
        f.writeBytes(bytes)
        val out = ArrayList<Bitmap>()
        ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { r ->
                for (i in 0 until r.pageCount) {
                    r.openPage(i).use { p ->
                        val h = (widthPx.toFloat() * p.height / p.width).toInt()
                        val b = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
                        b.eraseColor(Color.WHITE)
                        p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        out.add(b)
                    }
                }
            }
        }
        return out
    }
}
