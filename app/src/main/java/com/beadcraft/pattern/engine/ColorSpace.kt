package com.beadcraft.pattern.engine

/**
 * 色彩空间转换与色差计算
 * 移植自 Beanify (https://github.com/zhoucanzong/Beanify) 的 color-space.ts
 * 支持 sRGB -> XYZ -> Lab，以及感知均匀的 CIEDE2000 色差。
 */
object ColorSpace {

    // D65 标准光源
    private const val REF_X = 95.047
    private const val REF_Y = 100.0
    private const val REF_Z = 108.883

    private fun srgbToLinear(v: Int): Double {
        val c = v / 255.0
        return if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }

    /** RGB [0-255] -> Lab，写入 out（长度 3）并返回 */
    fun rgbToLab(r: Int, g: Int, b: Int, out: DoubleArray): DoubleArray {
        val rl = srgbToLinear(r)
        val gl = srgbToLinear(g)
        val bl = srgbToLinear(b)

        var x = (rl * 0.4124564 + gl * 0.3575761 + bl * 0.1804375) * 100.0 / REF_X
        var y = (rl * 0.2126729 + gl * 0.7151522 + bl * 0.0721750) * 100.0 / REF_Y
        var z = (rl * 0.0193339 + gl * 0.1191920 + bl * 0.9503041) * 100.0 / REF_Z

        x = if (x > 0.008856) Math.cbrt(x) else 7.787 * x + 16.0 / 116.0
        y = if (y > 0.008856) Math.cbrt(y) else 7.787 * y + 16.0 / 116.0
        z = if (z > 0.008856) Math.cbrt(z) else 7.787 * z + 16.0 / 116.0

        out[0] = 116 * y - 16
        out[1] = 500 * (x - y)
        out[2] = 200 * (y - z)
        return out
    }

    fun rgbToLab(r: Int, g: Int, b: Int): DoubleArray =
        rgbToLab(r, g, b, DoubleArray(3))

    /** CIE76 平方距离（聚类用，快速） */
    fun deltaE76Squared(l1: DoubleArray, l2: DoubleArray): Double {
        val dl = l1[0] - l2[0]
        val da = l1[1] - l2[1]
        val db = l1[2] - l2[2]
        return dl * dl + da * da + db * db
    }

    /**
     * 加权 CIE76 平方距离：色度 (a,b) 权重高于亮度 (L)，
     * 拼豆场景中色相/饱和度差异比明度差异更醒目。
     */
    fun deltaE76SquaredWeighted(l1: DoubleArray, l2: DoubleArray, wL: Double = 1.0, wAB: Double = 1.5): Double {
        val dl = (l1[0] - l2[0]) * wL
        val da = (l1[1] - l2[1]) * wAB
        val db = (l1[2] - l2[2]) * wAB
        return dl * dl + da * da + db * db
    }

    /** CIEDE2000 感知均匀色差 */
    fun deltaE2000(l1: DoubleArray, l2: DoubleArray): Double {
        val L1 = l1[0]; val a1 = l1[1]; val b1 = l1[2]
        val L2 = l2[0]; val a2 = l2[1]; val b2 = l2[2]

        val C1 = Math.sqrt(a1 * a1 + b1 * b1)
        val C2 = Math.sqrt(a2 * a2 + b2 * b2)
        val cBar = (C1 + C2) / 2.0

        val cBar7 = Math.pow(cBar, 7.0)
        val G = 0.5 * (1 - Math.sqrt(cBar7 / (cBar7 + Math.pow(25.0, 7.0))))

        val a1p = a1 * (1 + G)
        val a2p = a2 * (1 + G)

        val c1p = Math.sqrt(a1p * a1p + b1 * b1)
        val c2p = Math.sqrt(a2p * a2p + b2 * b2)

        val h1p = Math.atan2(b1, a1p)
        val h2p = Math.atan2(b2, a2p)

        val dLp = L2 - L1
        val dCp = c2p - c1p

        var dhp = 0.0
        if (c1p * c2p != 0.0) {
            dhp = h2p - h1p
            if (dhp > Math.PI) dhp -= 2 * Math.PI
            if (dhp < -Math.PI) dhp += 2 * Math.PI
        }

        val dHp = 2 * Math.sqrt(c1p * c2p) * Math.sin(dhp / 2)

        val lBarP = (L1 + L2) / 2.0
        val cBarP = (c1p + c2p) / 2.0

        var hBarP: Double
        if (c1p * c2p == 0.0) {
            hBarP = h1p + h2p
        } else {
            hBarP = (h1p + h2p) / 2.0
            if (Math.abs(h1p - h2p) > Math.PI) {
                hBarP += if (h1p + h2p < 2 * Math.PI) Math.PI else -Math.PI
            }
        }

        val t = 1 - 0.17 * Math.cos(hBarP - Math.PI / 6) +
            0.24 * Math.cos(2 * hBarP) +
            0.32 * Math.cos(3 * hBarP + Math.PI / 30) -
            0.20 * Math.cos(4 * hBarP - 21 * Math.PI / 20)

        val dTheta = (Math.PI / 6) *
            Math.exp(-Math.pow((hBarP * 180 / Math.PI - 275) / 25.0, 2.0))

        val cBarP7 = Math.pow(cBarP, 7.0)
        val rC = 2 * Math.sqrt(cBarP7 / (cBarP7 + Math.pow(25.0, 7.0)))
        val rT = -Math.sin(2 * dTheta) * rC

        val sL = 1 + (0.015 * Math.pow(lBarP - 50, 2.0)) / Math.sqrt(20 + Math.pow(lBarP - 50, 2.0))
        val sC = 1 + 0.045 * cBarP
        val sH = 1 + 0.015 * cBarP * t

        val tL = dLp / sL
        val tC = dCp / sC
        val tH = dHp / sH

        return Math.sqrt(tL * tL + tC * tC + tH * tH + rT * tC * tH)
    }

    /** 计算相对亮度，用于选择图纸色号文字的黑/白 */
    fun luminance(r: Int, g: Int, b: Int): Double {
        val rl = srgbToLinear(r)
        val gl = srgbToLinear(g)
        val bl = srgbToLinear(b)
        return 0.2126 * rl + 0.7152 * gl + 0.0722 * bl
    }
}
