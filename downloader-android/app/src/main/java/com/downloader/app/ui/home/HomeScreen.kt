package com.downloader.app.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.downloader.app.data.HistoryEntry
import com.downloader.app.ui.components.AppDialog
import com.downloader.app.ui.components.AppTextField
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.downloader.app.data.ErrorType
import com.downloader.app.data.Fmt
import com.downloader.app.data.MediaInfo
import com.downloader.app.data.MediaKind
import com.downloader.app.data.PlaylistItem
import com.downloader.app.data.QualityOption
import com.downloader.app.data.hint
import com.downloader.app.data.message
import com.downloader.app.ui.components.Badge
import com.downloader.app.ui.components.BrandMark
import com.downloader.app.ui.components.GoldButton
import com.downloader.app.ui.components.QuietButton
import com.downloader.app.ui.components.SectionLabel
import com.downloader.app.ui.components.Shimmer
import com.downloader.app.ui.components.Thumb
import com.downloader.app.ui.theme.AppTheme
import com.downloader.app.ui.theme.NumberStyle
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(vm: HomeViewModel, bottomInset: Dp, onOpenLogin: () -> Unit, modifier: Modifier = Modifier) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val ready = ui.state as? HomeState.Ready
    val ctaVisible = ready != null

    Box(modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = bottomInset + if (ctaVisible) 96.dp else 16.dp),
        ) {
            item("header") { Header() }
            item("hero") {
                AnimatedVisibility(
                    ui.state is HomeState.Idle,
                    enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically(),
                ) { Hero() }
            }
            item("input") {
                UrlInput(
                    value = ui.url,
                    onChange = vm::onUrlChange,
                    onClear = vm::clear,
                    onSubmit = { focus.clearFocus(); vm.analyze() },
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuietButton("لصق من الحافظة", { focus.clearFocus(); vm.paste() }, Modifier.weight(1.35f), icon = Icons.Rounded.ContentPaste)
                    if (ui.state is HomeState.Loading) {
                        QuietButton("إلغاء", vm::cancelAnalysis, Modifier.weight(1f), icon = Icons.Rounded.Close)
                    } else if (ready == null) {
                        GoldButton(
                            "تحليل", { focus.clearFocus(); vm.analyze() }, Modifier.weight(1f),
                            enabled = ui.url.isNotBlank(), loading = ui.state is HomeState.Loading, icon = Icons.Rounded.Search,
                        )
                    } else {
                        QuietButton("رابط جديد", vm::clear, Modifier.weight(1f), icon = Icons.Rounded.Refresh)
                    }
                }
            }
            item("clipboard") {
                AnimatedVisibility(
                    ui.clipboardUrl != null && ui.state !is HomeState.Loading,
                    enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically(),
                ) {
                    ClipboardSuggestion(ui.clipboardUrl.orEmpty(), vm::useClipboardSuggestion, vm::dismissClipboardSuggestion)
                }
            }

            when (val state = ui.state) {
                HomeState.Idle -> item("idle") { IdleHints() }
                HomeState.Loading -> item("loading") { LoadingCard() }
                is HomeState.Failed -> item("error") {
                    ErrorCard(state.type, state.detail, onRetry = vm::analyze, onLogin = onOpenLogin)
                }
                is HomeState.Ready -> {
                    val info = state.info
                    item("media") { MediaCard(info) }
                    ui.duplicate?.let { prev -> item("duplicate") { DuplicateBanner(prev) } }
                    if (info.isDirect) {
                        item("direct") { DirectSummary(info) }
                    } else {
                        if (info.isPlaylist && (!info.viaGallery || info.items.size > 1)) {
                            item("items-label") {
                                Spacer(Modifier.height(22.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    SectionLabel("العناصر", Modifier.weight(1f))
                                    val all = ui.selectedItems.size == info.items.size
                                    Text(
                                        if (all) "إلغاء تحديد الكل" else "تحديد الكل",
                                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = vm::toggleAllItems).padding(horizontal = 10.dp, vertical = 8.dp),
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                            itemsIndexed(info.items, key = { _, it -> "pl-${it.index}" }) { i, entry ->
                                Stagger(i.coerceAtMost(8), entry.index) {
                                    PlaylistRow(entry, entry.index in ui.selectedItems) { vm.toggleItem(entry.index) }
                                }
                            }
                        }
                        if (info.viaGallery) {
                            item("gallery") { Stagger(0, "gallery") { OriginalQualityCard(info.items.size) } }
                        } else {
                        item("mode") {
                            Spacer(Modifier.height(22.dp))
                            if (info.videoOptions.isNotEmpty() && info.hasAudio) {
                                ModeToggle(ui.audioMode, vm::setAudioMode)
                                Spacer(Modifier.height(16.dp))
                            }
                            if (!info.isPlaylist) {
                                OptionsCard(info, ui, vm)
                            }
                        }
                        if (ui.audioMode || info.videoOptions.isEmpty()) {
                            item("audio") {
                                Stagger(0, "audio") { AudioCard(ui.audioFormat, info.audioSizeBytes.takeIf { !info.isPlaylist }) }
                            }
                        } else {
                            item("q-label") {
                                SectionLabel("الجودة", trailing = Fmt.options(info.videoOptions.size))
                                Spacer(Modifier.height(10.dp))
                            }
                            itemsIndexed(info.videoOptions, key = { _, it -> it.id }) { i, option ->
                                Stagger(i, option.id) {
                                    QualityRow(option, selected = option.id == ui.selectedOptionId, top = i == 0) { vm.selectOption(option.id) }
                                }
                            }
                        }
                        }
                    }
                }
            }
        }

        // زر التحميل الذهبي ثابت أسفل الشاشة عند ظهور النتيجة.
        AnimatedVisibility(
            ctaVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            val info = ready?.info
            Box(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.96f), MaterialTheme.colorScheme.background)))
                    .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = bottomInset + 12.dp),
            ) {
                if (info != null) {
                    val option = info.videoOptions.firstOrNull { it.id == ui.selectedOptionId }
                    val audio = ui.audioMode || (option == null && !info.isDirect && !info.viaGallery)
                    val caption = when {
                        info.isDirect -> listOfNotNull("ملف مباشر", info.directFiles.first().sizeBytes?.let { Fmt.bytes(it) }).joinToString("  ·  ") { Fmt.iso(it) }
                        info.viaGallery -> if (info.items.size > 1) "${ui.selectedItems.size} من ${info.items.size}  ·  الجودة الأصلية" else "الجودة الأصلية"
                        info.isPlaylist -> "${ui.selectedItems.size} من ${info.items.size}  ·  " + (if (audio) "صوت ${ui.audioFormat.uppercase()}" else option?.label.orEmpty())
                        else -> {
                            val trimmed = ui.trimStart != null && ui.trimEnd != null && (info.durationSec ?: 0) > 0
                            val fraction = if (trimmed) (ui.trimEnd!! - ui.trimStart!!).toDouble() / info.durationSec!! else 1.0
                            val size = (if (audio) info.audioSizeBytes else option?.sizeBytes)?.let { (it * fraction).toLong() }
                            listOfNotNull(
                                if (audio) "صوت ${ui.audioFormat.uppercase()}" else option?.label,
                                if (trimmed) "مقطع ${Fmt.clock(ui.trimEnd!! - ui.trimStart!!)}" else null,
                                size?.let { (if (audio || trimmed || option?.sizeApprox == true) "نحو " else "") + Fmt.bytes(it) },
                            ).joinToString("  ·  ") { Fmt.iso(it) }
                        }
                    }
                    GoldButton(
                        "تحميل", { vm.download() }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Download, caption = caption,
                        enabled = !info.isPlaylist || ui.selectedItems.isNotEmpty(),
                    )
                }
            }
        }
    }
}

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 18.dp)) {
        BrandMark(26.dp)
        Spacer(Modifier.width(10.dp))
        Text("داونلودر", style = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold), color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun Hero() {
    Column(Modifier.padding(top = 10.dp, bottom = 22.dp)) {
        Text("أي رابط.", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface)
        Text("بأعلى جودة.", style = MaterialTheme.typography.displaySmall.copy(brush = AppTheme.extra.goldBrush))
        Spacer(Modifier.height(8.dp))
        Text(
            "ألصق رابط فيديو أو صورة أو مقطع صوتي، اختر الجودة، والباقي يتم على جوالك.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UrlInput(value: String, onChange: (String) -> Unit, onClear: () -> Unit, onSubmit: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val border by animateColorAsState(if (focused) AppTheme.extra.gold.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline, label = "border")
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, border, MaterialTheme.shapes.medium)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Link, null, tint = if (focused) AppTheme.extra.gold else AppTheme.extra.dim, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text("ألصق الرابط هنا", style = MaterialTheme.typography.bodyLarge, color = AppTheme.extra.dim)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface, textDirection = TextDirection.Ltr, textAlign = TextAlign.Left, fontSize = 15.sp,
                ),
                cursorBrush = SolidColor(AppTheme.extra.gold),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            )
        }
        if (value.isNotEmpty()) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClear),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Close, "مسح", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun ClipboardSuggestion(url: String, onUse: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("وجدنا رابطاً منسوخاً", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    url, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "استخدامه", style = MaterialTheme.typography.labelLarge, color = AppTheme.extra.gold,
                modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onUse).padding(horizontal = 12.dp, vertical = 12.dp),
            )
            Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, "تجاهل", tint = AppTheme.extra.dim, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdleHints() {
    Column(Modifier.padding(top = 30.dp)) {
        SectionLabel("يعمل مع")
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("يوتيوب", "انستجرام", "تيك توك", "تويتر / X", "فيسبوك", "سناب شات", "بنترست", "وأكثر من ألف موقع").forEach {
                Text(
                    it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
        Spacer(Modifier.height(26.dp))
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Tip("١", "انسخ الرابط من أي تطبيق وألصقه هنا.")
                Tip("٢", "أو اضغط «مشاركة» في التطبيق الآخر واختر داونلودر.")
                Tip("٣", "الملف يبقى داخل التطبيق حتى تضغط «حفظ في الجهاز».")
            }
        }
    }
}

@Composable
private fun Tip(n: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(n, style = MaterialTheme.typography.titleMedium, color = AppTheme.extra.dim, modifier = Modifier.width(26.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LoadingCard() {
    Column(Modifier.padding(top = 22.dp)) {
        Shimmer(Modifier.fillMaxWidth().aspectRatio(16f / 9f), MaterialTheme.shapes.large)
        Spacer(Modifier.height(14.dp))
        Shimmer(Modifier.fillMaxWidth(0.8f).height(18.dp))
        Spacer(Modifier.height(8.dp))
        Shimmer(Modifier.fillMaxWidth(0.4f).height(14.dp))
        Spacer(Modifier.height(20.dp))
        Text("جاري تحليل الرابط واستخراج الجودات…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        repeat(3) {
            Shimmer(Modifier.fillMaxWidth().height(60.dp), MaterialTheme.shapes.medium)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ErrorCard(type: ErrorType, detail: String?, onRetry: () -> Unit, onLogin: () -> Unit) {
    val icon: ImageVector = when (type) {
        ErrorType.UNSUPPORTED -> Icons.Rounded.LinkOff
        ErrorType.PRIVATE -> Icons.Rounded.Lock
        ErrorType.NO_INTERNET -> Icons.Rounded.WifiOff
        else -> Icons.Rounded.ErrorOutline
    }
    Surface(
        modifier = Modifier.padding(top = 22.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.28f)),
    ) {
        Column(Modifier.padding(20.dp)) {
            Box(
                Modifier.size(48.dp).background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp)) }
            Spacer(Modifier.height(14.dp))
            Text(type.message(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(type.hint(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!detail.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    detail, style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.dim, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.small).padding(10.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (type == ErrorType.PRIVATE) {
                    QuietButton("تسجيل الدخول", onLogin, Modifier.weight(1f), icon = Icons.Rounded.Lock, compact = true)
                }
                QuietButton("إعادة المحاولة", onRetry, Modifier.weight(1f), icon = Icons.Rounded.Refresh, compact = true)
            }
        }
    }
}

@Composable
private fun MediaCard(info: MediaInfo) {
    Column(Modifier.padding(top = 22.dp)) {
        Box {
            val kind = info.directFiles.firstOrNull()?.kind
            Thumb(
                info.thumbnail,
                Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                fallback = when (kind) {
                    MediaKind.IMAGE -> Icons.Rounded.Image
                    MediaKind.AUDIO -> Icons.Rounded.MusicNote
                    MediaKind.OTHER -> Icons.Rounded.InsertDriveFile
                    else -> Icons.Rounded.Movie
                },
                shape = MaterialTheme.shapes.large,
            )
            Box(
                Modifier.matchParentSize().clip(MaterialTheme.shapes.large)
                    .background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color(0xCC000000)))
            )
            Row(Modifier.align(Alignment.BottomStart).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                info.platform?.let { Chip(it) }
                if (info.isPlaylist) Chip(Fmt.elements(info.items.size))
            }
            Fmt.duration(info.durationSec)?.let {
                Chip(it, Modifier.align(Alignment.BottomEnd).padding(12.dp), number = true)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(info.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis)
        info.uploader?.let {
            Spacer(Modifier.height(2.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Chip(text: String, modifier: Modifier = Modifier, number: Boolean = false) {
    Text(
        text,
        style = if (number) MaterialTheme.typography.labelMedium.merge(NumberStyle) else MaterialTheme.typography.labelSmall,
        color = Color(0xFFEFE9DC),
        modifier = modifier.background(Color(0xB3000000), MaterialTheme.shapes.extraSmall).padding(horizontal = 8.dp, vertical = 3.dp),
        maxLines = 1,
    )
}

@Composable
private fun DirectSummary(info: MediaInfo) {
    val f = info.directFiles.first()
    Surface(
        modifier = Modifier.padding(top = 18.dp).fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer, border = BorderStroke(1.dp, AppTheme.extra.gold.copy(alpha = 0.5f)),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    when (f.kind) {
                        MediaKind.IMAGE -> "صورة — تحميل مباشر"
                        MediaKind.VIDEO -> "فيديو — تحميل مباشر"
                        MediaKind.AUDIO -> "صوت — تحميل مباشر"
                        MediaKind.OTHER -> "ملف — تحميل مباشر"
                    },
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    listOfNotNull(f.fileName.substringAfterLast('.', "").uppercase().takeIf { it.isNotEmpty() && it.length <= 5 }, "بالجودة الأصلية").joinToString("  ·  ") { Fmt.iso(it) },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(Fmt.bytes(f.sizeBytes), style = MaterialTheme.typography.titleSmall.merge(NumberStyle), color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun ModeToggle(audio: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        @Composable
        fun Seg(label: String, icon: ImageVector, on: Boolean, click: () -> Unit) {
            val bg by animateColorAsState(if (on) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent, label = "seg")
            Row(
                Modifier.weight(1f).heightIn(min = 46.dp).clip(RoundedCornerShape(12.dp)).background(bg).clickable(onClick = click),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                Icon(icon, null, tint = if (on) MaterialTheme.colorScheme.onSurface else AppTheme.extra.dim, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Seg("فيديو", Icons.Rounded.Movie, !audio) { onChange(false) }
        Seg("صوت فقط", Icons.Rounded.MusicNote, audio) { onChange(true) }
    }
}

/** ظهور متدرّج: كل صف يدخل بعد الذي قبله بقليل. */
@Composable
private fun Stagger(index: Int, key: Any, content: @Composable () -> Unit) {
    var visible by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) {
        delay(60L * index + 40)
        visible = true
    }
    AnimatedVisibility(
        visible,
        enter = fadeIn(tween(280)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { it / 3 },
    ) { content() }
    if (!visible) Spacer(Modifier.height(68.dp))
}

@Composable
private fun QualityRow(option: QualityOption, selected: Boolean, top: Boolean, onClick: () -> Unit) {
    val extra = AppTheme.extra
    val border by animateColorAsState(if (selected) extra.gold else MaterialTheme.colorScheme.outlineVariant, label = "qb")
    val bg by animateColorAsState(if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow, label = "qbg")
    Row(
        Modifier.padding(bottom = 8.dp).fillMaxWidth().heightIn(min = 60.dp)
            .clip(MaterialTheme.shapes.medium).background(bg)
            .border(1.dp, border, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // مؤشر الاختيار
        Box(
            Modifier.size(22.dp).border(1.5.dp, if (selected) extra.gold else MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(selected, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "dot") { on ->
                if (on) Box(Modifier.size(12.dp).background(extra.goldBrush, CircleShape)) else Spacer(Modifier.size(12.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(option.label, style = MaterialTheme.typography.titleMedium.merge(NumberStyle), color = MaterialTheme.colorScheme.onSurface)
                option.badge?.let { Badge(it, gold = top) }
            }
            Text(option.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (option.sizeBytes != null) Fmt.bytes(option.sizeBytes) else "—",
                style = MaterialTheme.typography.titleSmall.merge(NumberStyle),
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (option.sizeBytes != null && option.sizeApprox) {
                Text("تقريبي", style = MaterialTheme.typography.labelSmall, color = extra.dim)
            }
        }
    }
}

@Composable
private fun OriginalQualityCard(count: Int) {
    Surface(
        Modifier.padding(top = 18.dp).fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer, border = BorderStroke(1.dp, AppTheme.extra.gold.copy(alpha = 0.5f)),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Image, null, tint = AppTheme.extra.gold, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("الملفات الأصلية", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    if (count > 1) "تُحمَّل العناصر المحددة بجودتها الأصلية كما رُفعت." else "يُحمَّل بجودته الأصلية كما رُفع.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AudioCard(format: String, size: Long?) {
    Surface(
        Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, AppTheme.extra.gold),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.MusicNote, null, tint = AppTheme.extra.gold, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("صوت فقط · ${format.uppercase()}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text("أعلى جودة صوت متاحة. غيّر الصيغة من الإعدادات.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (size != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(Fmt.bytes(size), style = MaterialTheme.typography.titleSmall.merge(NumberStyle), color = MaterialTheme.colorScheme.onSurface)
                    Text("تقريبي", style = MaterialTheme.typography.labelSmall, color = AppTheme.extra.dim)
                }
            }
        }
    }
}

@Composable
private fun PlaylistRow(item: PlaylistItem, checked: Boolean, onToggle: () -> Unit) {
    val extra = AppTheme.extra
    Row(
        Modifier.padding(bottom = 6.dp).fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .background(if (checked) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onToggle).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(item.thumbnail, Modifier.size(width = 84.dp, height = 52.dp), fallback = if (item.title.contains("صورة")) Icons.Rounded.Image else Icons.Rounded.Movie)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (checked) 1f else 0.6f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Fmt.duration(item.durationSec)?.let { Text(it, style = MaterialTheme.typography.labelSmall.merge(NumberStyle), color = extra.dim) }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(24.dp).clip(RoundedCornerShape(7.dp))
                .then(if (checked) Modifier.background(extra.goldBrush) else Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(7.dp))),
            contentAlignment = Alignment.Center,
        ) { if (checked) Icon(Icons.Rounded.Check, null, tint = extra.onGold, modifier = Modifier.size(16.dp)) }
        Spacer(Modifier.width(6.dp))
    }
}

@Composable
private fun DuplicateBanner(previous: HistoryEntry) {
    Row(
        Modifier.padding(top = 14.dp).fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
            .border(1.dp, AppTheme.extra.gold.copy(alpha = 0.35f), MaterialTheme.shapes.medium)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.History, null, tint = AppTheme.extra.gold, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text("سبق أن حمّلت هذا الرابط", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                listOfNotNull(Fmt.date(previous.at), previous.label.takeIf { it.isNotBlank() }).joinToString("  ·  ") { Fmt.iso(it) },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** خيارات إضافية للفيديو المفرد: مسار الصوت، الترجمة، قصّ مقطع. تظهر فقط عندما تكون متاحة. */
@Composable
private fun OptionsCard(info: MediaInfo, ui: HomeUi, vm: HomeViewModel) {
    var dialog by remember { mutableStateOf<String?>(null) }
    val showAudio = info.audioTracks.isNotEmpty()
    val showSubs = info.subtitles.isNotEmpty() && !ui.audioMode
    val duration = info.durationSec ?: 0
    val showTrim = duration > 1
    if (!showAudio && !showSubs && !showTrim) return

    Surface(
        Modifier.padding(bottom = 18.dp).fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            var first = true
            @Composable
            fun Row_(icon: ImageVector, title: String, value: String, active: Boolean, key: String) {
                if (!first) Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                first = false
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 54.dp).clickable { dialog = key }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, null, tint = if (active) AppTheme.extra.gold else AppTheme.extra.dim, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        value, style = MaterialTheme.typography.labelMedium,
                        color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f), textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null, tint = AppTheme.extra.dim, modifier = Modifier.size(18.dp))
                }
            }
            if (showAudio) {
                val track = info.audioTracks.firstOrNull { it.lang == ui.audioLang }
                Row_(Icons.Rounded.RecordVoiceOver, "مسار الصوت",
                    track?.let { Fmt.language(it.lang) + if (it.original) " (الأصلي)" else "" } ?: "الافتراضي", track != null && !track.original, "audio")
            }
            if (showSubs) {
                Row_(Icons.Rounded.Subtitles, "الترجمة",
                    ui.subtitle?.let { Fmt.language(it.lang) + "  ·  " + if (ui.subEmbed) "مدمجة" else "ملف SRT" } ?: "بدون", ui.subtitle != null, "subs")
            }
            if (showTrim) {
                val on = ui.trimStart != null && ui.trimEnd != null
                Row_(Icons.Rounded.ContentCut, "قصّ مقطع",
                    if (on) Fmt.iso("${Fmt.clock(ui.trimStart!!)} – ${Fmt.clock(ui.trimEnd!!)}") else "الفيديو كاملاً", on, "trim")
            }
        }
    }

    when (dialog) {
        "audio" -> AppDialog("مسار الصوت", onDismiss = { dialog = null }, dismissText = "إغلاق") {
            info.audioTracks.forEach { t ->
                ChoiceRow(Fmt.language(t.lang), if (t.original) "الأصلي" else null, t.lang == ui.audioLang) { vm.setAudioLang(t.lang); dialog = null }
            }
        }
        "subs" -> {
            var embed by remember { mutableStateOf(ui.subEmbed) }
            AppDialog("الترجمة", onDismiss = { dialog = null }, dismissText = "إغلاق") {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp)).padding(3.dp),
                ) {
                    listOf(true to "دمج داخل الفيديو", false to "ملف SRT مستقل").forEach { (value, label) ->
                        Box(
                            Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(9.dp))
                                .background(if (embed == value) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
                                .clickable { embed = value; if (ui.subtitle != null) vm.setSubtitle(ui.subtitle, value) },
                            contentAlignment = Alignment.Center,
                        ) { Text(label, style = MaterialTheme.typography.labelMedium, color = if (embed == value) AppTheme.extra.gold else MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                Text(
                    if (embed) "تُضاف كمسار داخل الملف يمكن إظهاره أو إخفاؤه من المشغّل." else "يُحفظ ملف الترجمة بجانب الفيديو باسمه نفسه.",
                    style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.dim, modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                )
                ChoiceRow("بدون ترجمة", null, ui.subtitle == null) { vm.setSubtitle(null, embed); dialog = null }
                info.subtitles.forEach { t ->
                    ChoiceRow(Fmt.language(t.lang), if (t.auto) "آلية" else null, t == ui.subtitle) { vm.setSubtitle(t, embed); dialog = null }
                }
            }
        }
        "trim" -> TrimDialog(
            duration, ui.trimStart, ui.trimEnd,
            onApply = { a, b -> vm.setTrim(a, b); dialog = null },
            onClear = { vm.setTrim(null, null); dialog = null },
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun ChoiceRow(label: String, badge: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(MaterialTheme.shapes.small).clickable(onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        if (badge != null) {
            Spacer(Modifier.width(8.dp))
            Badge(badge)
        }
        Spacer(Modifier.weight(1f))
        if (selected) Icon(Icons.Rounded.Check, null, tint = AppTheme.extra.gold, modifier = Modifier.size(20.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrimDialog(duration: Long, start: Long?, end: Long?, onApply: (Long, Long) -> Unit, onClear: () -> Unit, onDismiss: () -> Unit) {
    var range by remember { mutableStateOf((start ?: 0L).toFloat()..(end ?: duration).toFloat()) }
    var from by remember { mutableStateOf(Fmt.clock(start ?: 0L)) }
    var to by remember { mutableStateOf(Fmt.clock(end ?: duration)) }
    val a = Fmt.parseTime(from)
    val b = Fmt.parseTime(to)
    val valid = a != null && b != null && a < b && b <= duration
    AppDialog(
        "قصّ مقطع", onDismiss = onDismiss,
        confirmText = "تطبيق", onConfirm = { if (valid) onApply(a!!, b!!) },
        dismissText = if (start != null) "إلغاء القصّ" else "إغلاق",
        onDismissButton = if (start != null) onClear else null,
    ) {
        Text("اختر بداية ونهاية الجزء الذي تريد تحميله. مدة الفيديو ${Fmt.clock(duration)}.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        // شريط الزمن يبقى من اليسار لليمين كما في كل المشغّلات.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            RangeSlider(
                value = range,
                onValueChange = { r ->
                    val s = r.start.toLong().coerceIn(0, duration - 1)
                    val e = r.endInclusive.toLong().coerceIn(s + 1, duration)
                    range = s.toFloat()..e.toFloat()
                    from = Fmt.clock(s); to = Fmt.clock(e)
                },
                valueRange = 0f..duration.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = AppTheme.extra.gold, activeTrackColor = AppTheme.extra.gold,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("من", style = MaterialTheme.typography.labelSmall, color = AppTheme.extra.dim)
                AppTextField(from, { v -> from = v; Fmt.parseTime(v)?.let { t -> if (t < range.endInclusive) range = t.toFloat()..range.endInclusive } }, ltr = true)
            }
            Column(Modifier.weight(1f)) {
                Text("إلى", style = MaterialTheme.typography.labelSmall, color = AppTheme.extra.dim)
                AppTextField(to, { v -> to = v; Fmt.parseTime(v)?.let { t -> if (t > range.start && t <= duration) range = range.start..t.toFloat() } }, ltr = true)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (valid) "مدة المقطع ${Fmt.clock(b!! - a!!)}. القصّ يتم عند أقرب نقطة قطع في الفيديو، فقد يزيد أو ينقص ثوانيَ قليلة."
            else "اكتب الوقت بصيغة دقائق:ثوانٍ (مثل 1:30) وتأكد أن النهاية بعد البداية وضمن مدة الفيديو.",
            style = MaterialTheme.typography.bodySmall, color = if (valid) AppTheme.extra.dim else MaterialTheme.colorScheme.error,
        )
    }
}
