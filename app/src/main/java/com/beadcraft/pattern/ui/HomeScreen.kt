package com.beadcraft.pattern.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beadcraft.pattern.MainViewModel
import com.beadcraft.pattern.UiState
import com.beadcraft.pattern.engine.BeadDatabase
import com.beadcraft.pattern.engine.PatternMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val GRID_PRESETS = listOf(29, 32, 52, 58, 64, 87, 100)

@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.selectImage(uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) {
        // ---------- MIUI 大标题 ----------
        Spacer(Modifier.height(20.dp))
        Text("拼豆工坊", style = MaterialTheme.typography.headlineMedium, fontSize = 30.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "照片一键变拼豆图纸 · 多品牌色号精准匹配",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // ---------- 选择图片 ----------
        SectionTitle("选择图片", subtitle = "${state.imageWidth}×${state.imageHeight}".takeIf { state.imageWidth > 0 })
        ImagePickerCard(
            state = state,
            onPick = {
                pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        )

        // ---------- 模式 ----------
        SectionTitle("制作模式")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ModeCard(
                emoji = "🖼",
                title = "全图制作",
                desc = "画面全部铺满豆板",
                selected = state.mode == PatternMode.FULL,
                onClick = { viewModel.setMode(PatternMode.FULL) },
                modifier = Modifier.weight(1f),
            )
            ModeCard(
                emoji = "✂️",
                title = "提取主体",
                desc = "自动去背景只拼主体",
                selected = state.mode == PatternMode.SUBJECT,
                onClick = { viewModel.setMode(PatternMode.SUBJECT) },
                modifier = Modifier.weight(1f),
            )
        }

        // ---------- 品牌 ----------
        SectionTitle("豆子品牌", subtitle = "色卡数据来自公开整理")
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.height(((BeadDatabase.BRANDS.size + 1) / 2 * 112).dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            userScrollEnabled = false,
        ) {
            items(BeadDatabase.BRANDS.size) { i ->
                val brand = BeadDatabase.BRANDS[i]
                BrandCard(
                    name = brand.displayName,
                    description = brand.description,
                    previewColors = viewModel.brandPreviews[brand.id] ?: emptyList(),
                    selected = state.brandId == brand.id,
                    onClick = { viewModel.setBrand(brand.id) },
                )
            }
        }

        // ---------- 尺寸 ----------
        SectionTitle("图纸尺寸", subtitle = "长边 ${state.gridLongSide} 格")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GRID_PRESETS.forEach { preset ->
                SelectChip(
                    text = "$preset",
                    selected = state.gridLongSide == preset,
                    onClick = { viewModel.setGridSize(preset) },
                )
            }
        }
        Slider(
            value = state.gridLongSide.toFloat(),
            onValueChange = { viewModel.setGridSize(it.toInt()) },
            valueRange = 20f..120f,
            steps = 99,
        )

        // ---------- 颜色数 ----------
        SectionTitle("颜色数量", subtitle = "最多 ${state.maxColors} 色")
        Slider(
            value = state.maxColors.toFloat(),
            onValueChange = { viewModel.setMaxColors(it.toInt()) },
            valueRange = 5f..60f,
            steps = 54,
        )

        // ---------- 生成 ----------
        Spacer(Modifier.height(22.dp))
        if (state.processing) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(10.dp))
                Text("正在生成图纸…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            PrimaryButton(
                text = if (state.imageUri == null) "先选择一张图片" else "生成图纸 ✨",
                enabled = state.imageUri != null,
                onClick = { viewModel.generate() },
            )
        }
        state.error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "🔒 全部本地处理，图片不会上传",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ImagePickerCard(state: UiState, onPick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = shape,
            )
            .clickable { onPick() },
        contentAlignment = Alignment.Center,
    ) {
        if (state.imageUri == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🖼", fontSize = 44.sp)
                Spacer(Modifier.height(10.dp))
                Text("点击选择图片", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "支持 JPG / PNG / WebP",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val preview = rememberAsyncThumbnail(state.imageUri!!)
            preview?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } ?: run {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
            }
        }
    }
}

@Composable
private fun ModeCard(
    emoji: String,
    title: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = shape,
            )
            .clickable { onClick() }
            .padding(16.dp),
    ) {
        Column {
            Text(emoji, fontSize = 26.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                title, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                desc, style = MaterialTheme.typography.bodySmall, lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 轻量缩略图加载（不引入图片库依赖） */
@Composable
private fun rememberAsyncThumbnail(uri: android.net.Uri): Bitmap? {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                var sample = 1
                var longest = maxOf(bounds.outWidth, bounds.outHeight)
                if (longest > 0) {
                    while (longest / sample > 1024) sample *= 2
                    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, opts)
                    }
                } else null
            }.getOrNull()
        }
    }
    return bitmap
}
