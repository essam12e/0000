package com.downloader.app.ui.library

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.downloader.app.data.DownloadTask
import com.downloader.app.data.Fmt
import com.downloader.app.data.GallerySaver
import com.downloader.app.data.HistoryEntry
import com.downloader.app.data.LibraryItem
import com.downloader.app.data.MediaKind
import com.downloader.app.data.TaskState
import com.downloader.app.ui.components.AppDialog
import com.downloader.app.ui.components.AppTextField
import com.downloader.app.ui.components.BrandMark
import com.downloader.app.ui.components.EmptyState
import com.downloader.app.ui.components.GoldProgress
import com.downloader.app.ui.components.QuietButton
import com.downloader.app.ui.components.RoundIconButton
import com.downloader.app.ui.components.SectionLabel
import com.downloader.app.ui.components.Thumb
import com.downloader.app.ui.theme.AppTheme
import com.downloader.app.ui.theme.NumberStyle
import java.io.File

fun shareItems(context: Context, items: List<LibraryItem>) {
    if (items.isEmpty()) return
    runCatching {
        val uris = items.map { FileProvider.getUriForFile(context, "${context.packageName}.files", File(it.path)) }
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).setType(items.first().mime).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "مشاركة").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

fun shareItem(context: Context, item: LibraryItem) = shareItems(context, listOf(item))

fun kindIcon(kind: MediaKind) = when (kind) {
    MediaKind.VIDEO -> Icons.Rounded.Movie
    MediaKind.AUDIO -> Icons.Rounded.MusicNote
    MediaKind.IMAGE -> Icons.Rounded.Image
    MediaKind.OTHER -> Icons.Rounded.InsertDriveFile
}

fun kindName(kind: MediaKind) = when (kind) {
    MediaKind.VIDEO -> "فيديو"
    MediaKind.AUDIO -> "صوت"
    MediaKind.IMAGE -> "صورة"
    MediaKind.OTHER -> "ملف"
}

@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    bottomInset: Dp,
    onOpen: (LibraryItem) -> Unit,
    onSave: (LibraryItem) -> Unit,
    onSaveMany: (List<LibraryItem>) -> Unit,
    onReopenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val all by vm.allItems.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showHistory by rememberSaveable { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var sortDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<List<LibraryItem>?>(null) }
    var confirmSave by remember { mutableStateOf<List<LibraryItem>?>(null) }
    var renaming by remember { mutableStateOf<LibraryItem?>(null) }
    var confirmClearHistory by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()

    BackHandler(enabled = selecting) { vm.clearSelection() }

    Column(modifier.fillMaxSize().statusBarsPadding()) {
        // ---------- الرأس: عنوان عادي، أو شريط إجراءات التحديد المتعدد
        Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp)) {
            if (selecting) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 64.dp)) {
                    RoundIconButton(Icons.Rounded.Close, "إلغاء التحديد", vm::clearSelection)
                    Spacer(Modifier.width(12.dp))
                    Text("${selected.size} محدد", style = MaterialTheme.typography.titleLarge.merge(NumberStyle), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RoundIconButton(Icons.Rounded.DoneAll, "تحديد الكل", vm::selectAll)
                        RoundIconButton(Icons.Rounded.SaveAlt, "حفظ المحدد في الجهاز", { confirmSave = vm.selectedItems() }, tint = AppTheme.extra.gold)
                        RoundIconButton(Icons.Rounded.Share, "مشاركة المحدد", { shareItems(context, vm.selectedItems()) })
                        RoundIconButton(Icons.Rounded.DeleteOutline, "حذف المحدد", { confirmDelete = vm.selectedItems() }, tint = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 64.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("تحميلاتي", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            if (all.isEmpty()) "ملفاتك تبقى هنا حتى تحفظها في الجهاز"
                            else Fmt.iso(Fmt.files(all.size)) + "  ·  " + Fmt.iso(Fmt.bytes(all.sumOf { it.size })),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!showHistory && all.isNotEmpty()) {
                        RoundIconButton(Icons.Rounded.Search, "بحث", { searching = !searching; if (!searching) vm.setQuery("") },
                            tint = if (searching) AppTheme.extra.gold else MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(8.dp))
                        RoundIconButton(Icons.AutoMirrored.Rounded.Sort, "فرز", { sortDialog = true },
                            tint = if (sort != SortMode.NEWEST) AppTheme.extra.gold else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        // ---------- تبويب داخلي: الملفات / السجلّ
        if (!selecting) {
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 10.dp).fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                @Composable
                fun Seg(label: String, on: Boolean, click: () -> Unit) {
                    val bg by animateColorAsState(if (on) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent, label = "seg")
                    Box(
                        Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(12.dp)).background(bg).clickable(onClick = click),
                        contentAlignment = Alignment.Center,
                    ) { Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Seg("الملفات", !showHistory) { showHistory = false }
                Seg("السجلّ" + if (history.isNotEmpty()) "  ${history.size}" else "", showHistory) { showHistory = true }
            }
        }

        AnimatedVisibility(searching && !showHistory && !selecting, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            AppTextField(
                query, vm::setQuery, Modifier.padding(horizontal = 20.dp).padding(bottom = 10.dp).fillMaxWidth(),
                placeholder = "ابحث باسم الملف", leading = Icons.Rounded.Search,
                trailing = {
                    if (query.isNotEmpty()) Box(Modifier.size(36.dp).clip(CircleShape).clickable { vm.setQuery("") }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Close, "مسح", tint = AppTheme.extra.dim, modifier = Modifier.size(18.dp))
                    }
                },
            )
        }

        if (showHistory && !selecting) {
            HistoryList(history, bottomInset, onReopenUrl, vm::removeHistory, onClear = { confirmClearHistory = true })
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = bottomInset + 16.dp),
            ) {
                if (tasks.isNotEmpty() && !selecting) {
                    item("active-label") {
                        SectionLabel("قيد التحميل", trailing = "${tasks.size}")
                        Spacer(Modifier.height(10.dp))
                    }
                    items(tasks, key = { "t-${it.id}" }) { task ->
                        TaskCard(task, onPause = { vm.pause(task.id) }, onResume = { vm.resume(task.id) }, onCancel = { vm.cancel(task.id) })
                    }
                    item("gap") { Spacer(Modifier.height(8.dp)) }
                }

                if (all.isNotEmpty()) {
                    if (!selecting) item("files-label") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionLabel(if (query.isBlank()) "داخل التطبيق" else "نتائج البحث (${items.size})", Modifier.weight(1f))
                            QuietButton("حفظ الكل في الجهاز", { confirmSave = items }, icon = Icons.Rounded.SaveAlt, compact = true, enabled = saving.isEmpty() && items.isNotEmpty())
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    items(items, key = { "f-${it.id}" }) { item ->
                        FileCard(
                            item, saving = item.id in saving, selecting = selecting, selected = item.id in selected,
                            onClick = { if (selecting) vm.toggleSelect(item.id) else onOpen(item) },
                            onLongClick = { vm.toggleSelect(item.id) },
                            onSave = { onSave(item) },
                            onShare = { shareItem(context, item) },
                            onRename = { renaming = item },
                            onDelete = { confirmDelete = listOf(item) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    if (items.isEmpty()) item("no-results") {
                        Text("لا يوجد ملف بهذا الاسم.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 24.dp))
                    }
                    if (!selecting && items.isNotEmpty()) item("hint") {
                        Text("اضغط مطوّلاً على ملف لتحديد عدة ملفات.", style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.dim, modifier = Modifier.padding(top = 4.dp))
                    }
                }

                if (tasks.isEmpty() && all.isEmpty()) {
                    item("empty") {
                        EmptyState(
                            "لا توجد تحميلات بعد",
                            "ألصق رابطاً في الرئيسية وسيظهر الملف هنا مع معاينة وتشغيل داخل التطبيق.",
                            Modifier.padding(top = 72.dp),
                        ) { BrandMark(40.dp) }
                    }
                }
            }
        }
    }

    // ---------- الحوارات
    if (sortDialog) {
        AppDialog("فرز الملفات", onDismiss = { sortDialog = false }, dismissText = "إغلاق") {
            SortMode.entries.forEach { mode ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(MaterialTheme.shapes.small)
                        .clickable { vm.setSort(mode); sortDialog = false }.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(mode.label, style = MaterialTheme.typography.bodyLarge,
                        color = if (mode == sort) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    if (mode == sort) Icon(Icons.Rounded.Check, null, tint = AppTheme.extra.gold, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
    confirmDelete?.let { list ->
        AppDialog(
            if (list.size == 1) "حذف الملف؟" else "حذف ${Fmt.files(list.size)}؟", onDismiss = { confirmDelete = null },
            confirmText = "حذف نهائي", danger = true,
            onConfirm = { if (list.size == 1) vm.delete(list.first()) else vm.deleteMany(list); confirmDelete = null },
        ) {
            Text(
                (if (list.size == 1) "سيُحذف «${list.first().title}» من التطبيق نهائياً." else "ستُحذف الملفات المحددة من التطبيق نهائياً.") +
                    " إن أردت الاحتفاظ بها فاحفظها في الجهاز أولاً.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    confirmSave?.let { list ->
        AppDialog(
            "حفظ ${Fmt.files(list.size)} في الجهاز؟", onDismiss = { confirmSave = null }, confirmText = "حفظ",
            onConfirm = { confirmSave = null; onSaveMany(list) },
        ) {
            Text(
                "تُنسخ إلى «التنزيلات ‹ ${GallerySaver.ROOT}» مرتّبة في مجلدات: فيديو، صوتيات، صور، أخرى. كل ملف ينجح حفظه يُزال من التطبيق، وما يفشل يبقى هنا كما هو.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    renaming?.let { item ->
        var name by remember(item.id) { mutableStateOf(item.title) }
        AppDialog(
            "إعادة تسمية", onDismiss = { renaming = null }, confirmText = "حفظ الاسم",
            onConfirm = { if (name.isNotBlank()) { vm.rename(item, name); renaming = null } },
        ) {
            AppTextField(name, { name = it }, Modifier.fillMaxWidth(), placeholder = "اسم الملف", singleLine = false)
            Text("الامتداد (${File(item.path).extension.uppercase()}) يبقى كما هو.", style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.dim, modifier = Modifier.padding(top = 8.dp))
        }
    }
    if (confirmClearHistory) {
        AppDialog("مسح السجلّ؟", onDismiss = { confirmClearHistory = false }, confirmText = "مسح السجلّ", danger = true,
            onConfirm = { vm.clearHistory(); confirmClearHistory = false }) {
            Text("يُمسح سجلّ الروابط فقط؛ ملفاتك لا تُمسّ. بعدها لن يُنبَّه عند تكرار رابط قديم.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HistoryList(entries: List<HistoryEntry>, bottomInset: Dp, onOpen: (String) -> Unit, onRemove: (String) -> Unit, onClear: () -> Unit) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = bottomInset + 16.dp)) {
        if (entries.isEmpty()) {
            item("empty") {
                EmptyState("السجلّ فارغ", "كل رابط تحمّله يُسجَّل هنا، وننبّهك إذا حاولت تحميله مرة أخرى.", Modifier.padding(top = 72.dp)) {
                    Icon(Icons.Rounded.History, null, tint = AppTheme.extra.gold, modifier = Modifier.size(40.dp))
                }
            }
        } else {
            item("label") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("روابط حمّلتها", Modifier.weight(1f))
                    QuietButton("مسح السجلّ", onClear, icon = Icons.Rounded.DeleteOutline, compact = true)
                }
                Spacer(Modifier.height(10.dp))
            }
            items(entries, key = { "h-${it.id}" }) { e ->
                Surface(
                    modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth().animateItem(), shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(Modifier.clickable { onOpen(e.url) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Thumb(e.thumbnail, Modifier.size(width = 72.dp, height = 48.dp), fallback = Icons.Rounded.Movie)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(e.platform, e.label.takeIf { it.isNotBlank() }).joinToString("  ·  ") { Fmt.iso(it) },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(Fmt.date(e.at), style = MaterialTheme.typography.labelSmall.merge(NumberStyle), color = AppTheme.extra.dim)
                        }
                        Box(Modifier.size(40.dp).clip(CircleShape).clickable {
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("link", e.url))
                        }, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.ContentCopy, "نسخ الرابط", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
                        Box(Modifier.size(40.dp).clip(CircleShape).clickable { onRemove(e.id) }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Close, "إزالة من السجلّ", tint = AppTheme.extra.dim, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskCard(task: DownloadTask, onPause: () -> Unit, onResume: () -> Unit, onCancel: () -> Unit) {
    val extra = AppTheme.extra
    val running = task.state == TaskState.RUNNING
    val failed = task.state == TaskState.FAILED
    Surface(
        modifier = Modifier.padding(bottom = 10.dp).fillMaxWidth().animateContentSize(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, if (failed) MaterialTheme.colorScheme.error.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Thumb(task.spec.thumbnail, Modifier.size(width = 72.dp, height = 48.dp), fallback = Icons.Rounded.Movie)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(task.spec.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(task.spec.qualityLabel.takeIf { it.isNotBlank() }, task.spec.expectedBytes?.let { Fmt.bytes(it) }).joinToString("  ·  ") { Fmt.iso(it) },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    )
                }
                Spacer(Modifier.width(8.dp))
                when (task.state) {
                    TaskState.RUNNING, TaskState.QUEUED -> RoundIconButton(Icons.Rounded.Pause, "إيقاف مؤقت", onPause)
                    TaskState.PAUSED -> RoundIconButton(Icons.Rounded.PlayArrow, "استكمال", onResume, tint = extra.gold)
                    TaskState.FAILED -> RoundIconButton(Icons.Rounded.Refresh, "إعادة المحاولة", onResume, tint = extra.gold)
                }
                Spacer(Modifier.width(8.dp))
                RoundIconButton(Icons.Rounded.Close, "إلغاء", onCancel, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(14.dp))
            if (failed) {
                Text(task.error ?: "فشل التحميل", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 3, overflow = TextOverflow.Ellipsis)
            } else {
                GoldProgress(if (task.state == TaskState.QUEUED) 0f else task.progress, active = running)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (task.state) {
                            TaskState.QUEUED -> "في الانتظار"
                            TaskState.PAUSED -> "متوقف مؤقتاً"
                            else -> task.stage ?: "جاري التحميل"
                        },
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (running) {
                        val meta = listOfNotNull(Fmt.speed(task.speed) ?: task.speed, Fmt.eta(task.etaSec)).joinToString("  ·  ") { Fmt.iso(it) }
                        if (meta.isNotEmpty()) {
                            Text(meta, style = MaterialTheme.typography.labelSmall.merge(NumberStyle), color = extra.dim, maxLines = 1)
                            Spacer(Modifier.width(10.dp))
                        }
                    }
                    if (task.progress >= 0 && task.state != TaskState.QUEUED) {
                        Text(Fmt.percent(task.progress), style = MaterialTheme.typography.titleSmall.merge(NumberStyle), color = if (running) extra.gold else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileCard(
    item: LibraryItem,
    saving: Boolean,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extra = AppTheme.extra
    val border by animateColorAsState(if (selected) extra.gold else MaterialTheme.colorScheme.outlineVariant, label = "sel")
    Surface(
        modifier = modifier.padding(bottom = 10.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, border),
    ) {
        Column {
            Row(Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Thumb(
                        when (item.kind) {
                            MediaKind.VIDEO, MediaKind.IMAGE -> File(item.path)
                            MediaKind.AUDIO -> item.thumbUrl
                            MediaKind.OTHER -> null
                        },
                        Modifier.size(width = 104.dp, height = 70.dp), fallback = kindIcon(item.kind),
                    )
                    if (!selecting && (item.kind == MediaKind.VIDEO || item.kind == MediaKind.AUDIO)) {
                        Box(
                            Modifier.align(Alignment.Center).size(30.dp).background(Color(0x99000000), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.PlayArrow, "تشغيل", tint = Color.White, modifier = Modifier.size(20.dp)) }
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        listOfNotNull(
                            kindName(item.kind), File(item.path).extension.uppercase().takeIf { it.isNotEmpty() },
                            Fmt.bytes(item.size), Fmt.duration(item.durationSec),
                        ).joinToString("  ·  ") { Fmt.iso(it) },
                        style = MaterialTheme.typography.bodySmall.merge(NumberStyle.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    )
                }
                if (selecting) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(24.dp).clip(CircleShape)
                            .then(if (selected) Modifier.background(extra.goldBrush) else Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)),
                        contentAlignment = Alignment.Center,
                    ) { if (selected) Icon(Icons.Rounded.Check, "محدد", tint = extra.onGold, modifier = Modifier.size(16.dp)) }
                }
            }
            if (!selecting) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuietButton("حفظ في الجهاز", onSave, Modifier.weight(1f), icon = Icons.Rounded.SaveAlt, compact = true, loading = saving)
                    RoundIconButton(Icons.Rounded.Share, "مشاركة", onShare, enabled = !saving)
                    RoundIconButton(Icons.Rounded.Edit, "إعادة تسمية", onRename, enabled = !saving)
                    RoundIconButton(Icons.Rounded.DeleteOutline, "حذف", onDelete, tint = MaterialTheme.colorScheme.error, enabled = !saving)
                }
            }
        }
    }
}
