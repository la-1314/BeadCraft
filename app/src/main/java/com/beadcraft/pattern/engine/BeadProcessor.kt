package com.beadcraft.pattern.engine

import android.graphics.Bitmap
import android.graphics.Color

/** 图纸生成模式 */
enum class PatternMode { FULL, SUBJECT }

/** 全图制作：整个画面铺满豆板；提取主体：自动剥离背景，只拼主体 */
data class ProcessOptions(
    val gridLongSide: Int = 58,
    val maxColors: Int = 24,
    val mode: PatternMode = PatternMode.FULL,
    val denoise: Boolean = true,
)

/** 生成结果：网格中每格存储调色板下标，-1 表示空格 */
class BeadPattern(
    val width: Int,
    val height: Int,
    val grid: IntArray,
    val usedColors: List<BeadColor>,
    val usedCounts: IntArray,
    val totalBeads: Int,
    val brandId: String,
) {
    val colorCount: Int get() = usedColors.size
}

/**
 * 拼豆图纸生成引擎
 *
 * 管线（学习自 Beanify）：
 *   主体提取(flood-fill) -> 盒式降采样到网格 -> CIEDE2000 最近色量化
 *   -> 最大颜色数限制(两遍量化) -> 小连通域去噪 -> 用料统计
 */
object BeadProcessor {

    /** 主体提取时的工作上限（长边像素） */
    private const val DETECT_MAX_SIDE = 384
    /** 背景与边缘种子色的 Lab 距离阈值 */
    private const val BG_TOLERANCE = 22.0
    /** 判定网格为空所需的最小背景占比 */
    private const val EMPTY_RATIO = 0.62

    // ------------------------------------------------------------------
    // 主入口
    // ------------------------------------------------------------------

    fun process(
        src: Bitmap,
        brandId: String,
        opts: ProcessOptions,
        palette: List<BeadColor>,
        paletteLabs: Array<DoubleArray>,
    ): BeadPattern {
        // 1. 主体提取（SUBJECT 模式），返回裁剪后的位图 + 同尺寸背景掩码
        var working = src
        var bgMask: BooleanArray? = null
        if (opts.mode == PatternMode.SUBJECT) {
            val extracted = extractSubject(src)
            if (extracted != null) {
                working = extracted.first
                bgMask = extracted.second
            }
        }

        // 2. 网格尺寸：长边 = gridLongSide，另一边按裁剪后图像比例
        val longSide = opts.gridLongSide.coerceIn(16, 160)
        val gw: Int
        val gh: Int
        if (working.width >= working.height) {
            gw = longSide
            gh = (longSide * working.height / working.width.toDouble()).toInt().coerceAtLeast(1)
        } else {
            gh = longSide
            gw = (longSide * working.width / working.height.toDouble()).toInt().coerceAtLeast(1)
        }

        // 3. 盒式降采样：累加 RGB 与背景占比，得到每格的平均色与是否为空
        val n = gw * gh
        val cellR = FloatArray(n)
        val cellG = FloatArray(n)
        val cellB = FloatArray(n)
        val cellEmpty = BooleanArray(n)
        quantizeCells(working, bgMask, gw, gh, cellR, cellG, cellB, cellEmpty)

        // 4. 第一遍量化：全色卡最近色（CIEDE2000）
        val grid = IntArray(n) { -1 }
        val lab = DoubleArray(3)
        val freq = HashMap<Int, Int>()
        for (i in 0 until n) {
            if (cellEmpty[i]) continue
            ColorSpace.rgbToLab(cellR[i].toInt(), cellG[i].toInt(), cellB[i].toInt(), lab)
            val idx = nearestColor(lab, paletteLabs)
            grid[i] = idx
            freq[idx] = (freq[idx] ?: 0) + 1
        }

        // 5. 限制颜色数：保留出现最多的 maxColors 种，其余重新指派到保留色
        if (freq.size > opts.maxColors) {
            val allowed = freq.entries
                .sortedByDescending { it.value }
                .take(opts.maxColors.coerceAtLeast(2))
                .map { it.key }
                .toIntArray()
            val allowedLabs = allowed.map { paletteLabs[it] }.toTypedArray()
            for (i in 0 until n) {
                if (grid[i] < 0) continue
                grid[i] = allowed[nearestColor(paletteLabs[grid[i]], allowedLabs)]
            }
        }

        // 6. 压缩为紧凑调色板（下标 0..k-1，空格 -1）
        val compactMap = HashMap<Int, Int>()
        val usedColors = ArrayList<BeadColor>()
        for (i in 0 until n) {
            val gi = grid[i]
            if (gi < 0) continue
            if (!compactMap.containsKey(gi)) {
                compactMap[gi] = usedColors.size
                usedColors.add(palette[gi])
            }
            grid[i] = compactMap[gi]!!
        }

        // 7. 小连通域去噪：≤2 格的孤立色块并入最常见邻色
        if (opts.denoise && usedColors.isNotEmpty()) {
            denoise(grid, gw, gh)
        }

        // 8. 统计
        val counts = IntArray(usedColors.size)
        var total = 0
        for (i in 0 until n) {
            val gi = grid[i]
            if (gi >= 0) {
                counts[gi]++
                total++
            }
        }
        return BeadPattern(gw, gh, grid, usedColors, counts, total, brandId)
    }

    // ------------------------------------------------------------------
    // 主体提取：从四边向内 flood-fill，识别连片背景
    // ------------------------------------------------------------------

    private fun extractSubject(src: Bitmap): Pair<Bitmap, BooleanArray>? {
        val w: Int
        val h: Int
        val scaled: Bitmap
        if (maxOf(src.width, src.height) > DETECT_MAX_SIDE) {
            val ratio = DETECT_MAX_SIDE.toDouble() / maxOf(src.width, src.height)
            w = (src.width * ratio).toInt().coerceAtLeast(8)
            h = (src.height * ratio).toInt().coerceAtLeast(8)
            scaled = Bitmap.createScaledBitmap(src, w, h, true)
        } else {
            w = src.width; h = src.height; scaled = src
        }

        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)

        val labs = Array(w * h) { DoubleArray(3) }
        for (i in pixels.indices) {
            val p = pixels[i]
            ColorSpace.rgbToLab(Color.red(p), Color.green(p), Color.blue(p), labs[i])
        }

        // 边缘像素索引
        val border = ArrayList<Int>(2 * (w + h))
        for (x in 0 until w) { border.add(x); border.add((h - 1) * w + x) }
        for (y in 0 until h) { border.add(y * w); border.add(y * w + w - 1) }

        // 边缘主色投票：RGB 通道 8 级量化分桶，取票数前 3 的桶
        val votes = HashMap<Long, Int>()
        for (i in border) {
            val p = pixels[i]
            if (Color.alpha(p) < 128) continue
            val key = quantKey(p)
            votes[key] = (votes[key] ?: 0) + 1
        }
        if (votes.isEmpty()) return null
        val topKeys = votes.entries.sortedByDescending { it.value }.take(3).map { it.key }.toSet()

        // 桶内（仅边缘像素）平均 Lab 作为背景种子色
        val acc = HashMap<Long, DoubleArray>()
        val accCnt = HashMap<Long, Int>()
        for (i in border) {
            val p = pixels[i]
            if (Color.alpha(p) < 128) continue
            val key = quantKey(p)
            if (key !in topKeys) continue
            val a = acc.getOrPut(key) { DoubleArray(3) }
            a[0] += labs[i][0]; a[1] += labs[i][1]; a[2] += labs[i][2]
            accCnt[key] = (accCnt[key] ?: 0) + 1
        }
        val seedLab = acc.map { (k, v) ->
            val c = accCnt[k]!!
            doubleArrayOf(v[0] / c, v[1] / c, v[2] / c)
        }
        if (seedLab.isEmpty()) return null

        // flood-fill：与任一种子主色的距离 < 阈值视为背景
        val isBg = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var head = 0
        var tail = 0

        fun tryMark(i: Int) {
            if (isBg[i]) return
            val p = pixels[i]
            if (Color.alpha(p) < 128) {
                isBg[i] = true
                queue[tail++] = i
                return
            }
            for (s in seedLab) {
                if (ColorSpace.deltaE2000(labs[i], s) < BG_TOLERANCE) {
                    isBg[i] = true
                    queue[tail++] = i
                    return
                }
            }
        }

        for (x in 0 until w) { tryMark(x); tryMark((h - 1) * w + x) }
        for (y in 0 until h) { tryMark(y * w); tryMark(y * w + w - 1) }

        while (head < tail) {
            val i = queue[head++]
            val x = i % w
            val y = i / w
            if (x > 0) tryMark(i - 1)
            if (x < w - 1) tryMark(i + 1)
            if (y > 0) tryMark(i - w)
            if (y < h - 1) tryMark(i + w)
        }

        // 主体占比过小则放弃提取
        var subjectCount = 0
        for (b in isBg) if (!b) subjectCount++
        if (subjectCount < w * h * 0.04) return null

        // 主体包围盒（留 2% 边距）
        var minX = w; var maxX = -1; var minY = h; var maxY = -1
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (!isBg[y * w + x]) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < minX || maxY < minY) return null

        val mx = ((maxX - minX + 1) * 0.02).toInt().coerceAtLeast(1)
        val my = ((maxY - minY + 1) * 0.02).toInt().coerceAtLeast(1)
        minX = (minX - mx).coerceAtLeast(0)
        maxX = (maxX + mx).coerceAtMost(w - 1)
        minY = (minY - my).coerceAtLeast(0)
        maxY = (maxY + my).coerceAtMost(h - 1)

        val bw = maxX - minX + 1
        val bh = maxY - minY + 1
        val cropX = (minX.toLong() * src.width / w).toInt().coerceIn(0, src.width - 1)
        val cropY = (minY.toLong() * src.height / h).toInt().coerceIn(0, src.height - 1)
        val cropW = (bw.toLong() * src.width / w).toInt().coerceIn(1, src.width - cropX)
        val cropH = (bh.toLong() * src.height / h).toInt().coerceIn(1, src.height - cropY)
        val cropped = Bitmap.createBitmap(src, cropX, cropY, cropW, cropH)

        // 掩码同步裁剪，供网格空格判定
        val croppedMask = BooleanArray(bw * bh)
        for (y in 0 until bh) {
            for (x in 0 until bw) {
                croppedMask[y * bw + x] = isBg[(minY + y) * w + minX + x]
            }
        }
        if (scaled !== src) scaled.recycle()
        return Pair(cropped, croppedMask)
    }

    private fun quantKey(p: Int): Long =
        ((Color.red(p) shr 5).toLong() shl 10) or
            ((Color.green(p) shr 5).toLong() shl 5) or
            (Color.blue(p) shr 5).toLong()

    // ------------------------------------------------------------------
    // 盒式降采样
    // ------------------------------------------------------------------

    private fun quantizeCells(
        bmp: Bitmap,
        bgMask: BooleanArray?,
        gw: Int, gh: Int,
        outR: FloatArray, outG: FloatArray, outB: FloatArray,
        outEmpty: BooleanArray,
    ) {
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)

        val sumR = DoubleArray(gw * gh)
        val sumG = DoubleArray(gw * gh)
        val sumB = DoubleArray(gw * gh)
        val cnt = IntArray(gw * gh)
        val bgCnt = IntArray(gw * gh)

        for (y in 0 until h) {
            val gy = (y.toLong() * gh / h).toInt().coerceAtMost(gh - 1)
            val rowOffset = y * w
            for (x in 0 until w) {
                val gx = (x.toLong() * gw / w).toInt().coerceAtMost(gw - 1)
                val ci = gy * gw + gx
                val p = pixels[rowOffset + x]
                cnt[ci]++
                if (Color.alpha(p) < 128) {
                    bgCnt[ci]++
                    continue
                }
                sumR[ci] += Color.red(p).toDouble()
                sumG[ci] += Color.green(p).toDouble()
                sumB[ci] += Color.blue(p).toDouble()
                if (bgMask != null && bgMask[rowOffset + x]) bgCnt[ci]++
            }
        }

        for (i in outEmpty.indices) {
            if (cnt[i] == 0 || bgCnt[i].toDouble() / cnt[i] >= EMPTY_RATIO) {
                outEmpty[i] = true
            } else {
                val solid = cnt[i] - bgCnt[i]
                if (solid <= 0) { outEmpty[i] = true; continue }
                outR[i] = (sumR[i] / solid).toFloat()
                outG[i] = (sumG[i] / solid).toFloat()
                outB[i] = (sumB[i] / solid).toFloat()
            }
        }
    }

    // ------------------------------------------------------------------
    // 最近色查找 & 去噪
    // ------------------------------------------------------------------

    private fun nearestColor(lab: DoubleArray, paletteLabs: Array<DoubleArray>): Int {
        var best = 0
        var bestD = Double.MAX_VALUE
        for (i in paletteLabs.indices) {
            val d = ColorSpace.deltaE2000(lab, paletteLabs[i])
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }

    /** 4-连通同色域中尺寸 ≤2 的碎块并入最常见的相邻颜色 */
    private fun denoise(grid: IntArray, gw: Int, gh: Int) {
        val n = gw * gh
        val visited = BooleanArray(n)
        val stack = IntArray(n)
        val component = IntArray(n)

        for (start in 0 until n) {
            if (visited[start] || grid[start] < 0) continue
            val color = grid[start]
            var top = 0
            var size = 0
            stack[top++] = start
            visited[start] = true
            while (top > 0) {
                val i = stack[--top]
                component[size++] = i
                val x = i % gw
                val y = i / gw
                if (x > 0 && !visited[i - 1] && grid[i - 1] == color) { visited[i - 1] = true; stack[top++] = i - 1 }
                if (x < gw - 1 && !visited[i + 1] && grid[i + 1] == color) { visited[i + 1] = true; stack[top++] = i + 1 }
                if (y > 0 && !visited[i - gw] && grid[i - gw] == color) { visited[i - gw] = true; stack[top++] = i - gw }
                if (y < gh - 1 && !visited[i + gw] && grid[i + gw] == color) { visited[i + gw] = true; stack[top++] = i + gw }
            }

            if (size in 1..2) {
                val neighborCount = HashMap<Int, Int>()
                for (k in 0 until size) {
                    val i = component[k]
                    val x = i % gw
                    val y = i / gw
                    fun check(j: Int) {
                        val c = grid[j]
                        if (c >= 0 && c != color) neighborCount[c] = (neighborCount[c] ?: 0) + 1
                    }
                    if (x > 0) check(i - 1)
                    if (x < gw - 1) check(i + 1)
                    if (y > 0) check(i - gw)
                    if (y < gh - 1) check(i + gw)
                }
                val target = neighborCount.entries.maxByOrNull { it.value }?.key
                if (target != null) {
                    for (k in 0 until size) grid[component[k]] = target
                }
            }
        }
    }
}
