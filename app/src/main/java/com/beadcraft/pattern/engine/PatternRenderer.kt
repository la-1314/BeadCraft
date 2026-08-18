package com.beadcraft.pattern.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/** 图纸位图渲染：用于导出 PNG / 分享 */
object PatternRenderer {

    /**
     * @param cellSize 每格像素大小
     * @param showGrid 是否绘制网格线
     * @param showCodes 是否在格子内标注色号
     */
    fun render(
        pattern: BeadPattern,
        cellSize: Int = 36,
        showGrid: Boolean = true,
        showCodes: Boolean = true,
        margin: Int = 8,
    ): Bitmap {
        val cs = cellSize.coerceAtLeast(8)
        val w = pattern.width * cs + margin * 2
        val h = pattern.height * cs + margin * 2
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D8DCE2")
            strokeWidth = 1f
        }
        val textDark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2B2F36")
            textAlign = Paint.Align.CENTER
        }
        val textLight = Paint(textDark).apply {
            color = Color.WHITE
        }

        val rect = RectF()
        val drawCodes = showCodes && cs >= 26

        for (y in 0 until pattern.height) {
            for (x in 0 until pattern.width) {
                val idx = pattern.grid[y * pattern.width + x]
                if (idx < 0) continue
                val c = pattern.usedColors[idx]
                val left = margin + x * cs.toFloat()
                val top = margin + y * cs.toFloat()
                rect.set(left, top, left + cs, top + cs)
                fill.color = Color.rgb(c.r, c.g, c.b)
                canvas.drawRect(rect, fill)

                if (drawCodes) {
                    val paint = if (ColorSpace.luminance(c.r, c.g, c.b) > 0.45) textDark else textLight
                    paint.textSize = cs * 0.26f
                    val cx = left + cs / 2f
                    val cy = top + cs / 2f - (paint.descent() + paint.ascent()) / 2f
                    canvas.drawText(compactCode(c.code), cx, cy, paint)
                }
            }
        }

        if (showGrid && cs >= 10) {
            for (x in 0..pattern.width) {
                val px = margin + x * cs.toFloat()
                canvas.drawLine(px, margin.toFloat(), px, h - margin.toFloat(), line)
            }
            for (y in 0..pattern.height) {
                val py = margin + y * cs.toFloat()
                canvas.drawLine(margin.toFloat(), py, w - margin.toFloat(), py, line)
            }
        }
        return bmp
    }

    /** 色号压缩显示：S01 -> 01，80-15213 -> 5213 */
    fun compactCode(code: String): String {
        val digits = code.filter { it.isDigit() }
        return when {
            digits.length >= 2 && digits.length <= 4 -> digits
            digits.length > 4 -> digits.takeLast(4)
            else -> code
        }
    }

    /** 图例区渲染：色号 + 名称 + 数量，拼在图纸下方，方便对着买豆 */
    fun renderWithLegend(pattern: BeadPattern, cellSize: Int, columns: Int = 4): Bitmap {
        val gridBmp = render(pattern, cellSize, showGrid = true, showCodes = true)
        val legend = renderLegend(pattern, gridBmp.width, columns)
        val w = maxOf(gridBmp.width, legend.width)
        val h = gridBmp.height + legend.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(gridBmp, (w - gridBmp.width) / 2f, 0f, null)
        canvas.drawBitmap(legend, (w - legend.width) / 2f, gridBmp.height.toFloat(), null)
        return out
    }

    private fun renderLegend(pattern: BeadPattern, width: Int, columns: Int): Bitmap {
        val entries = pattern.usedColors.indices
            .map { Triple(pattern.usedColors[it], it, pattern.usedCounts[it]) }
            .sortedByDescending { it.third }
        val rows = (entries.size + columns - 1) / columns
        val itemW = (width / columns).coerceAtLeast(120)
        val itemH = 44
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2B2F36")
            textSize = 15f
        }
        val swatch = Paint(Paint.ANTI_ALIAS_FLAG)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.parseColor("#E3E6EB")
        }

        val bmp = Bitmap.createBitmap(itemW * columns, rows * itemH + 12, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        entries.forEachIndexed { i, (color, _, count) ->
            val col = i % columns
            val row = i / columns
            val left = col * itemW + 10f
            val top = row * itemH + 10f
            swatch.color = Color.rgb(color.r, color.g, color.b)
            canvas.drawRoundRect(left, top, left + 26f, top + 26f, 7f, 7f, swatch)
            canvas.drawRoundRect(left, top, left + 26f, top + 26f, 7f, 7f, stroke)
            canvas.drawText("${color.code}  ${count}颗", left + 34f, top + 19f, paint)
        }
        return bmp
    }
}
