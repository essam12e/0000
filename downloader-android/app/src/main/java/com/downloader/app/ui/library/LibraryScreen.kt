package com.downloader.app.ui.library

import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.downloader.app.data.DownloadTask
import com.downloader.app.data.Fmt
import com.downloader.app.data.LibraryItem
import com.downloader.app.data.MediaKind
import com.downloader.app.data.TaskState
import com.downloader.app.ui.components.AppDialog
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

fun shareItem(context: Context, item: LibraryItem) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", File(item.path))
        val send = Intent(Intent.ACTION_SEND).setType(item.mime).putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "مشاركة").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

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
    onSaveAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf<LibraryItem?>(null) }
    var confirmSaveAll by remember { mutableStateOf(false) }

    LazyColumn(
        modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = bottomInset + 16.dp),
    ) {
        item("title") {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 6.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("تحميلاتي", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (items.isEmpty()) "ملفاتك تبقى هنا حتى تحفظها للمعرض"
                        else Fmt.iso(Fmt.files(items.size)) + "  ·  " + Fmt.iso(Fmt.bytes(items.sumOf { it.size })),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (tasks.isNotEmpty()) {
            item("active-label") {
                Spacer(Modifier.height(16.dp))
                SectionLabel("قيد التحميل", trailing = "${tasks.size}")
                Spacer(Modifier.height(10.dp))
            }
            items(tasks, key = { "t-${it.id}" }) { task ->
                TaskCard(task, onPause = { vm.pause(task.id) }, onResume = { vm.resume(task.id) }, onCancel = { vm.cancel(task.id) })
            }
        }

        if (items.isNotEmpty()) {
            item("files-label") {
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("داخل التطبيق", Modifier.weight(1f))
                    QuietButton("حفظ الكل للمعرض", { confirmSaveAll = true }, icon = Icons.Rounded.SaveAlt, compact = true, enabled = saving.isEmpty())
                }
                Spacer(Modifier.height(10.dp))
            }
            items(items, key = { "f-${it.id}" }) { item ->
                FileCard(
                    item, saving = item.id in saving,
                    onOpen = { onOpen(item) }, onSave = { onSave(item) },
                    onShare = { shareItem(context, item) }, onDelete = { confirmDelete = item },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        if (tasks.isEmpty() && items.isEmpty()) {
            item("empty") {
                EmptyState(
                    "لا توجد تحميلات بعد",
                    "ألصق رابطاً في الرئيسية وسيظهر الملف هنا مع معاينة وتشغيل داخل التطبيق.",
                    Modifier.padding(top = 96.dp),
                ) { BrandMark(40.dp) }
            }
        }
    }

    confirmDelete?.let { item ->
        AppDialog(
            "حذف الملف؟", onDismiss = { confirmDelete = null }, confirmText = "حذف نهائي", danger = true,
            onConfirm = { vm.delete(item); confirmDelete = null },
        ) {
            Text("سيُحذف «${item.title}» من التطبيق نهائياً. إن أردت الاحتفاظ به فاحفظه للمعرض أولاً.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (confirmSaveAll) {
        AppDialog(
            "حفظ الكل للمعرض؟", onDismiss = { confirmSaveAll = false }, confirmText = "حفظ الكل",
            onConfirm = { confirmSaveAll = false; onSaveAll() },
        ) {
            Text("ستُنسخ كل الملفات إلى المعرض، وكل ملف ينجح حفظه يُزال من التطبيق. ما يفشل يبقى هنا كما هو.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
private fun FileCard(
    item: LibraryItem,
    saving: Boolean,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.padding(bottom = 10.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Row(Modifier.clickable(onClick = onOpen).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Thumb(
                        when (item.kind) {
                            MediaKind.VIDEO, MediaKind.IMAGE -> File(item.path)
                            MediaKind.AUDIO -> item.thumbUrl
                            MediaKind.OTHER -> null
                        },
                        Modifier.size(width = 104.dp, height = 70.dp), fallback = kindIcon(item.kind),
                    )
                    if (item.kind == MediaKind.VIDEO || item.kind == MediaKind.AUDIO) {
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
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
            Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuietButton("حفظ للمعرض", onSave, Modifier.weight(1f), icon = Icons.Rounded.SaveAlt, compact = true, loading = saving)
                RoundIconButton(Icons.Rounded.Share, "مشاركة", onShare, enabled = !saving)
                RoundIconButton(Icons.Rounded.DeleteOutline, "حذف", onDelete, tint = MaterialTheme.colorScheme.error, enabled = !saving)
            }
        }
    }
}
