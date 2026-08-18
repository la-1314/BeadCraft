package com.beadcraft.pattern.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.beadcraft.pattern.engine.BeadPattern
import com.beadcraft.pattern.engine.ColorSpace
import com.beadcraft.pattern.engine.PatternRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ResultScreen(
    pattern: BeadPattern,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showCodes by remember { mutableStateOf(true) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val sorted = remember(pattern) {
        pattern.usedColors.indices
            .map { Triple(pattern.usedColors[it], pattern.usedCounts[it], it) }
            .sortedByDescending { it.second }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---------- 顶栏 ----------
        item {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "← 返回",
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onBack() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text("图纸预览", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(44.dp))
            }
        }

        // ---------- 图纸画布（双指缩放 / 拖动） ----------
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.5f, 8f)
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    },
            ) {
                PatternCanvas(pattern, showCodes, scale, Offset(offsetX, offsetY))
            }
        }

        // ---------- 统计 + 色号开关 ----------
        item {
            MiuiCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatCell("总豆数", "${pattern.totalBeads}")
                        StatCell("颜色", "${pattern.colorCount} 色")
                        StatCell("尺寸", "${pattern.width}×${pattern.height}")
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text("显示色号", fontWeight = FontWeight.SemiBold)
                            Text(
                                "放大后可查看每格色号",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = showCodes,
                            onCheckedChange = { showCodes = it },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
            }
        }

        // ---------- 操作按钮 ----------
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton(
                    text = "保存图纸 PNG",
                    modifier = Modifier.weight(1f),
                ) {
                    scope.launch { export(context, pattern, share = false) }
                }
                ActionButton(
                    text = "分享",
                    modifier = Modifier.weight(1f),
                ) {
                    scope.launch { export(context, pattern, share = true) }
                }
            }
        }

        // ---------- 用料清单 ----------
        item {
            SectionTitle("用料清单", subtitle = "按用量排序 · 可直接照着采购")
        }
        itemsIndexed(sorted) { _, (color, count, _) ->
            val maxCount = (sorted.firstOrNull()?.second ?: 1).coerceAtLeast(1)
            MiuiCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(android.graphics.Color.rgb(color.r, color.g, color.b))),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${color.code} · ${color.name}",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(5.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(count.toFloat() / maxCount)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "$count 颗",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

// --------------------------------------------------------------------------
// 图纸画布
// --------------------------------------------------------------------------

@Composable
private fun PatternCanvas(
    pattern: BeadPattern,
    showCodes: Boolean,
    scale: Float,
    offset: Offset,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val base = minOf(size.width / pattern.width, size.height / pattern.height) * 0.94f
        val cell = base * scale
        val gridW = pattern.width * cell
        val gridH = pattern.height * cell
        val startX = (size.width - gridW) / 2f + offset.x
        val startY = (size.height - gridH) / 2f + offset.y
        val cellSize = Size(cell + 1f, cell + 1f)

        // 底板
        drawRect(
            color = Color(0xFFF6F7F9),
            topLeft = Offset(startX, startY),
            size = Size(gridW, gridH),
        )

        // 豆子色块
        for (y in 0 until pattern.height) {
            val py = startY + y * cell
            for (x in 0 until pattern.width) {
                val idx = pattern.grid[y * pattern.width + x]
                if (idx < 0) continue
                val c = pattern.usedColors[idx]
                drawRect(
                    color = Color(android.graphics.Color.rgb(c.r, c.g, c.b)),
                    topLeft = Offset(startX + x * cell, py),
                    size = cellSize,
                )
            }
        }

        // 网格线（每 10 格加深，对齐拼豆板）
        if (cell > 7f) {
            val lineColor = Color(0x33888888)
            val strongColor = Color(0x66888888)
            for (x in 0..pattern.width) {
                drawLine(
                    color = if (x % 10 == 0) strongColor else lineColor,
                    start = Offset(startX + x * cell, startY),
                    end = Offset(startX + x * cell, startY + gridH),
                    strokeWidth = if (x % 10 == 0) 1.5f else 1f,
                )
            }
            for (y in 0..pattern.height) {
                drawLine(
                    color = if (y % 10 == 0) strongColor else lineColor,
                    start = Offset(startX, startY + y * cell),
                    end = Offset(startX + gridW, startY + y * cell),
                    strokeWidth = if (y % 10 == 0) 1.5f else 1f,
                )
            }
        }

        // 色号文字
        if (showCodes && cell >= 26f) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                textSize = cell * 0.26f
            }
            val nc = drawContext.canvas.nativeCanvas
            for (y in 0 until pattern.height) {
                for (x in 0 until pattern.width) {
                    val idx = pattern.grid[y * pattern.width + x]
                    if (idx < 0) continue
                    val c = pattern.usedColors[idx]
                    paint.color = if (ColorSpace.luminance(c.r, c.g, c.b) > 0.45)
                        android.graphics.Color.parseColor("#2B2F36")
                    else android.graphics.Color.WHITE
                    val cx = startX + x * cell + cell / 2f
                    val cy = startY + y * cell + cell / 2f - (paint.descent() + paint.ascent()) / 2f
                    nc.drawText(PatternRenderer.compactCode(c.code), cx, cy, paint)
                }
            }
        }
    }
}

// --------------------------------------------------------------------------
// 辅助组件与导出
// --------------------------------------------------------------------------

@Composable
private fun androidx.compose.foundation.layout.RowScope.StatCell(label: String, value: String) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActionButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
        )
    }
}

private suspend fun export(context: Context, pattern: BeadPattern, share: Boolean) {
    withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = PatternRenderer.renderWithLegend(pattern, cellSize = 34)
            if (share) {
                val dir = File(context.cacheDir, "export").apply { mkdirs() }
                val file = File(dir, "beadcraft_${System.currentTimeMillis()}.png")
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                withContext(Dispatchers.Main) {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "分享拼豆图纸"))
                }
            } else {
                saveToGallery(context, bitmap)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "已保存到相册 Pictures/BeadCraft", Toast.LENGTH_SHORT).show()
                }
            }
            bitmap.recycle()
        }.onFailure {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "导出失败：${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private fun saveToGallery(context: Context, bitmap: Bitmap) {
    val name = "beadcraft_${System.currentTimeMillis()}.png"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/BeadCraft")
        }
        val uri: Uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return
        context.contentResolver.openOutputStream(uri)?.use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    } else {
        @Suppress("DEPRECATION")
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "BeadCraft",
        ).apply { mkdirs() }
        val file = File(dir, name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        android.media.MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/png"), null)
    }
}
