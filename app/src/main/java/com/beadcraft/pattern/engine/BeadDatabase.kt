package com.beadcraft.pattern.engine

import android.content.Context

/** 单颗拼豆颜色 */
data class BeadColor(
    val code: String,
    val name: String,
    val r: Int,
    val g: Int,
    val b: Int,
    val brand: String,
)

/** 拼豆品牌定义 */
data class BeadBrand(
    val id: String,
    val displayName: String,
    val assetFile: String,
    val description: String,
)

/**
 * 品牌色号数据库
 * 色卡数据来自 Beanify 项目的调研整理（research 目录下的 CSV），
 * 覆盖 MARD / Perler / Hama / Artkal / Nabbi / Yant 六个主流品牌。
 */
object BeadDatabase {

    val BRANDS = listOf(
        BeadBrand("MARD", "MARD 麦德", "mard.csv", "国内主流 · 221 色"),
        BeadBrand("Artkal", "Artkal", "artkal_s.csv", "专业玩家向 · 197 色"),
        BeadBrand("Perler", "Perler", "perler.csv", "北美标准 · 101 色"),
        BeadBrand("Hama", "Hama 哈马", "hama.csv", "欧洲主流 · 90 色"),
        BeadBrand("Yant", "Yant", "yant.csv", "高性价比 · 117 色"),
        BeadBrand("Nabbi", "Nabbi 纳比", "nabbi.csv", "北欧经典 · 28 色"),
    )

    private val colorCache = HashMap<String, List<BeadColor>>()
    private val labCache = HashMap<String, Array<DoubleArray>>()

    /** 从 assets 加载品牌色卡（去重、缓存） */
    @Synchronized
    fun load(context: Context, brandId: String): List<BeadColor> {
        colorCache[brandId]?.let { return it }
        val brand = BRANDS.firstOrNull { it.id == brandId } ?: BRANDS.first()
        val colors = ArrayList<BeadColor>()
        val seen = HashSet<String>()
        context.assets.open("beads/${brand.assetFile}").bufferedReader().useLines { lines ->
            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty()) continue
                val parts = line.split(',')
                if (parts.size < 5) continue
                val code = parts[0].trim()
                if (code.isEmpty() || !seen.add(code)) continue
                val name = parts.getOrNull(1)?.trim().orEmpty().ifEmpty { code }
                val r = parts[2].trim().toIntOrNull() ?: continue
                val g = parts[3].trim().toIntOrNull() ?: continue
                val b = parts[4].trim().toIntOrNull() ?: continue
                colors.add(BeadColor(code, name, r, g, b, brand.id))
            }
        }
        colorCache[brandId] = colors
        labCache[brandId] = colors.map { ColorSpace.rgbToLab(it.r, it.g, it.b) }.toTypedArray()
        return colors
    }

    /** 预计算的品牌色卡 Lab 值 */
    @Synchronized
    fun paletteLabs(context: Context, brandId: String): Array<DoubleArray> {
        load(context, brandId)
        return labCache[brandId]!!
    }
}
