package com.downloader.app.ui.player

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.downloader.app.data.Fmt
import com.downloader.app.data.LibraryItem
import com.downloader.app.data.MediaKind
import com.downloader.app.ui.components.QuietButton
import com.downloader.app.ui.components.RoundIconButton
import com.downloader.app.ui.library.kindName
import com.downloader.app.ui.theme.AppTheme
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import java.io.File

/** معاينة وتشغيل داخل التطبيق: فيديو/صوت عبر ExoPlayer، وصور عبر Coil. */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    item: LibraryItem,
    saving: Boolean,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val playable = item.kind == MediaKind.VIDEO || item.kind == MediaKind.AUDIO
    val player = remember(item.id) {
        if (!playable) null
        else ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(item.path))))
            // الترجمة المدمجة: تُعرض العربية تلقائياً إن وُجدت، وزر CC في المشغّل للتبديل أو الإخفاء.
            trackSelectionParameters = trackSelectionParameters.buildUpon().setPreferredTextLanguages("ar", "ara").build()
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(item.id) { onDispose { player?.release() } }

    Column(Modifier.fillMaxSize().pointerInput(Unit) {}.background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "رجوع", onClose)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(kindName(item.kind), Fmt.bytes(item.size)).joinToString("  ·  ") { Fmt.iso(it) },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when {
                player != null -> {
                    if (item.kind == MediaKind.AUDIO && item.thumbUrl != null) {
                        AsyncImage(item.thumbUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, alpha = 0.35f)
                    }
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = {
                            PlayerView(it).apply {
                                this.player = player
                                setShowNextButton(false)
                                setShowPreviousButton(false)
                                setShowSubtitleButton(true)
                                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                                if (item.kind == MediaKind.AUDIO) { controllerShowTimeoutMs = 0; controllerHideOnTouch = false }
                                // عناصر التحكم بالوقت تبقى من اليسار لليمين كما في كل المشغّلات.
                                layoutDirection = android.view.View.LAYOUT_DIRECTION_LTR
                            }
                        },
                        onRelease = { it.player = null },
                    )
                }
                // تكبير بالقرص والنقر المزدوج (telephoto).
                item.kind == MediaKind.IMAGE -> ZoomableAsyncImage(File(item.path), item.title, Modifier.fillMaxSize())
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.InsertDriveFile, null, tint = AppTheme.extra.dim, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.size(12.dp))
                    Text("لا تتوفر معاينة لهذا النوع — استخدم «مشاركة» لفتحه بتطبيق آخر", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            QuietButton("حفظ في الجهاز", onSave, Modifier.weight(1f), icon = Icons.Rounded.SaveAlt, loading = saving)
            RoundIconButton(Icons.Rounded.Share, "مشاركة", onShare, Modifier.size(52.dp))
            RoundIconButton(Icons.Rounded.DeleteOutline, "حذف", onDelete, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.error)
        }
    }
}
