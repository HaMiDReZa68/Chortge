package com.example.smsaccounting

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.res.ResourcesCompat
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** kind: 0 = همهٔ تراکنش‌ها، 1 = دخل و خرج روزانه + جمع هر دسته؛ accountId = 0 یعنی همهٔ حساب‌ها */
data class ExportSpec(val kind: Int, val from: Long, val to: Long, val accountId: Long, val toman: Boolean)

class Section(val title: String, val headers: List<String>, val rows: List<List<Any>>)
class Table(val title: String, val sections: List<Section>)

object Export {
    fun table(ctx: Context, s: ExportSpec): Table {
        val db = Db.get(ctx)
        val accs = db.accounts().associateBy { it.id }
        val div = if (s.toman) 10L else 1L
        val unit = if (s.toman) "تومان" else "ریال"
        val list = db.query(s.from, s.to)
            .filter { s.accountId == 0L || it.accountId == s.accountId }
            .sortedBy { it.ts }
        val accName = if (s.accountId == 0L) "همهٔ حساب‌ها" else accs[s.accountId]?.name ?: ""
        val title = "گزارش ${Jalali.ymd(s.from)} تا ${Jalali.ymd(s.to - 1)} – $accName ($unit)"

        if (s.kind == 0) {
            val rows = list.map {
                val a = accs[it.accountId]
                listOf<Any>(
                    Jalali.ymd(it.ts), Jalali.time(it.ts).let { t -> Norm.normalize(t).map { c -> if (c in '۰'..'۹') '0' + (c - '۰') else c }.joinToString("") },
                    a?.let { x -> if (x.isCash) x.name else x.name + " / " + x.bank } ?: "",
                    if (it.type == TxType.INCOME) "دخل" else "خرج",
                    it.category, it.sub, it.amount / div, it.balance?.let { b -> b / div } ?: "",
                    it.note, it.merchant,
                )
            }
            return Table(
                title,
                listOf(
                    Section(
                        "تراکنش‌ها",
                        listOf("تاریخ", "ساعت", "حساب", "نوع", "بابت", "زیرشاخه", "مبلغ", "مانده", "توضیحات", "شرح"),
                        rows,
                    )
                ),
            )
        }

        val days = list.groupBy { Jalali.startOfDay(it.ts) }.toSortedMap()
        var ti = 0L
        var te = 0L
        val dayRows = ArrayList<List<Any>>()
        for ((d, l) in days) {
            val inc = l.filter { it.type == TxType.INCOME }.sumOf { it.amount } / div
            val exp = l.filter { it.type == TxType.EXPENSE }.sumOf { it.amount } / div
            ti += inc
            te += exp
            dayRows.add(listOf(Jalali.ymd(d), inc, exp, inc - exp))
        }
        dayRows.add(listOf("جمع", ti, te, ti - te))
        val cat = ArrayList<List<Any>>()
        for (t in listOf(TxType.EXPENSE, TxType.INCOME)) {
            val g = list.filter { it.type == t }.groupBy { it.category }
                .mapValues { e -> e.value.sumOf { it.amount } / div }
                .entries.sortedByDescending { it.value }
            for ((c, v) in g) cat.add(listOf(if (t == TxType.INCOME) "دخل" else "خرج", c, v))
        }
        return Table(
            title,
            listOf(
                Section("دخل و خرج روزانه", listOf("تاریخ", "دخل", "خرج", "مانده دوره"), dayRows),
                Section("جمع هر دسته", listOf("نوع", "دسته", "مبلغ"), cat),
            ),
        )
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .filter { it.code >= 32 || it == '\n' }

    private fun col(i: Int): String {
        var n = i
        val sb = StringBuilder()
        do {
            sb.insert(0, 'A' + n % 26)
            n = n / 26 - 1
        } while (n >= 0)
        return sb.toString()
    }

    fun xlsx(t: Table): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            fun put(name: String, text: String) {
                z.putNextEntry(ZipEntry(name))
                z.write(text.toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
            val n = t.sections.size
            put(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""" +
                    (1..n).joinToString("") { """<Override PartName="/xl/worksheets/sheet$it.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""" } +
                    "</Types>",
            )
            put(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
            )
            put(
                "xl/workbook.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""" +
                    t.sections.mapIndexed { i, s -> """<sheet name="${esc(s.title.take(30))}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""" }.joinToString("") +
                    "</sheets></workbook>",
            )
            put(
                "xl/_rels/workbook.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                    (1..n).joinToString("") { """<Relationship Id="rId$it" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$it.xml"/>""" } +
                    "</Relationships>",
            )
            t.sections.forEachIndexed { si, s ->
                val sb = StringBuilder()
                sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetViews><sheetView rightToLeft="1" workbookViewId="0"/></sheetViews><sheetData>""")
                fun cell(r: Int, c: Int, v: Any) {
                    val ref = col(c) + r
                    if (v is Long || v is Int) sb.append("""<c r="$ref"><v>$v</v></c>""")
                    else sb.append("""<c r="$ref" t="inlineStr"><is><t xml:space="preserve">${esc(v.toString())}</t></is></c>""")
                }
                sb.append("""<row r="1">""")
                s.headers.forEachIndexed { c, h -> cell(1, c, h) }
                sb.append("</row>")
                s.rows.forEachIndexed { ri, row ->
                    sb.append("""<row r="${ri + 2}">""")
                    row.forEachIndexed { c, v -> cell(ri + 2, c, v) }
                    sb.append("</row>")
                }
                sb.append("</sheetData></worksheet>")
                put("xl/worksheets/sheet${si + 1}.xml", sb.toString())
            }
        }
        return bos.toByteArray()
    }

    fun pdf(ctx: Context, t: Table): ByteArray {
        val tf = ResourcesCompat.getFont(ctx, R.font.vazirmatn_regular) ?: Typeface.DEFAULT
        val tfb = ResourcesCompat.getFont(ctx, R.font.vazirmatn_bold) ?: Typeface.DEFAULT_BOLD
        val doc = PdfDocument()
        val w = 595
        val h = 842
        val m = 28f
        val body = Paint().apply { typeface = tf; textSize = 8.5f; isAntiAlias = true; textAlign = Paint.Align.RIGHT }
        val head = Paint(body).apply { typeface = tfb }
        val big = Paint(body).apply { typeface = tfb; textSize = 12f }
        val line = Paint().apply { color = 0xFFCCCCCC.toInt(); strokeWidth = 0.5f }
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
        var cv = page.canvas
        var y = m + 6
        fun newPage() {
            doc.finishPage(page)
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
            cv = page.canvas
            y = m + 6
        }
        fun fit(s: String, p: Paint, maxW: Float): String {
            var r = Fa.digits(s)
            if (p.measureText(r) <= maxW) return r
            while (r.length > 1 && p.measureText("$r…") > maxW) r = r.dropLast(1)
            return "$r…"
        }
        cv.drawText(fit(t.title, big, w - 2 * m), w - m, y, big)
        y += 22
        for (s in t.sections) {
            if (y > h - 80) newPage()
            cv.drawText(s.title, w - m, y, head)
            y += 14
            val cols = s.headers.size
            val cw = (w - 2 * m) / cols
            fun row(vals: List<String>, p: Paint) {
                for (i in 0 until cols) {
                    val x = w - m - i * cw - 3f
                    cv.drawText(fit(vals.getOrElse(i) { "" }, p, cw - 6), x, y, p)
                }
                y += 4
                cv.drawLine(m, y, w - m, y, line)
                y += 10
            }
            row(s.headers, head)
            for (r in s.rows) {
                if (y > h - m) {
                    newPage()
                    row(s.headers, head)
                }
                row(r.map { if (it is Long || it is Int) Fa.money((it as Number).toLong()) else it.toString() }, body)
            }
            y += 12
        }
        doc.finishPage(page)
        val bos = ByteArrayOutputStream()
        doc.writeTo(bos)
        doc.close()
        return bos.toByteArray()
    }
}
