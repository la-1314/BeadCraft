package com.beadcraft.pattern

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.beadcraft.pattern.engine.BeadDatabase
import com.beadcraft.pattern.engine.BeadPattern
import com.beadcraft.pattern.engine.BeadProcessor
import com.beadcraft.pattern.engine.PatternMode
import com.beadcraft.pattern.engine.ProcessOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

 data class UiState(
    val imageUri: Uri? = null,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val brandId: String = "MARD",
    val mode: PatternMode = PatternMode.FULL,
    val gridLongSide: Int = 58,
    val maxColors: Int = 24,
    val processing: Boolean = false,
    val pattern: BeadPattern? = null,
    val error: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = getApplication<Application>()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** 品牌色卡懒加载（code -> 首色列表用于 UI 预览） */
    val brandPreviews: Map<String, List<Int>> by lazy {
        BeadDatabase.BRANDS.associate { b ->
            b.id to BeadDatabase.load(app, b.id).take(6).map { android.graphics.Color.rgb(it.r, it.g, it.b) }
        }
    }

    fun selectImage(uri: Uri) {
        viewModelScope.launch {
            val dims = withContext(Dispatchers.IO) {
                try {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                    opts.outWidth to opts.outHeight
                } catch (_: Exception) {
                    0 to 0
                }
            }
            _state.update {
                it.copy(imageUri = uri, imageWidth = dims.first, imageHeight = dims.second, pattern = null, error = null)
            }
        }
    }

    fun setBrand(id: String) = _state.update { it.copy(brandId = id) }
    fun setMode(mode: PatternMode) = _state.update { it.copy(mode = mode) }
    fun setGridSize(size: Int) = _state.update { it.copy(gridLongSide = size) }
    fun setMaxColors(n: Int) = _state.update { it.copy(maxColors = n) }

    fun backToHome() = _state.update { it.copy(pattern = null) }

    fun generate() {
        val s = _state.value
        val uri = s.imageUri ?: return
        if (s.processing) return
        _state.update { it.copy(processing = true, error = null) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    val bmp = decodeSampled(getApplication(), uri, 2048)
                        ?: throw IllegalStateException("无法读取图片")
                    try {
                        val palette = BeadDatabase.load(getApplication(), s.brandId)
                        val labs = BeadDatabase.paletteLabs(getApplication(), s.brandId)
                        BeadProcessor.process(
                            bmp, s.brandId,
                            ProcessOptions(s.gridLongSide, s.maxColors, s.mode),
                            palette, labs,
                        )
                    } finally {
                        bmp.recycle()
                    }
                }
                _state.update { it.copy(processing = false, pattern = result) }
            } catch (e: Exception) {
                _state.update { it.copy(processing = false, error = e.message ?: "生成失败，请换一张图片试试") }
            }
        }
    }

    private fun decodeSampled(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            var sample = 1
            var longest = maxOf(bounds.outWidth, bounds.outHeight)
            if (longest <= 0) return null
            while (longest / sample > maxSide * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null
            val cur = maxOf(decoded.width, decoded.height)
            if (cur > maxSide) {
                val ratio = maxSide.toFloat() / cur
                Bitmap.createScaledBitmap(decoded, (decoded.width * ratio).toInt().coerceAtLeast(1), (decoded.height * ratio).toInt().coerceAtLeast(1), true)
            } else decoded
        }.getOrNull()
    }
}
