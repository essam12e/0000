package com.downloader.app.ui.trim

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.downloader.app.data.AppError
import com.downloader.app.data.Fmt
import com.downloader.app.data.LibraryItem
import com.downloader.app.engine.Trimmer
import com.downloader.app.ui.components.GoldButton
import com.downloader.app.ui.components.RoundIconButton
import com.downloader.app.ui.theme.AppTheme
import com.downloader.app.ui.theme.NumberStyle
import kotlinx.coroutines.launch
import kotlin.math.abs

/** قصّ مقطع من فيديو في المكتبة: شريط لقطات بمقبضين + ضبط دقيق بالثانية. الأصل لا يتغيّر. */
@Composable
fun TrimScreen(item: LibraryItem, onClose: () -> Unit, onDone: (LibraryItem) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var duration by remember(item.id) { mutableLongStateOf(item.durationSec ?: 0L) }
    var frames by remember(item.id) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var start by remember(item.id) { mutableLongStateOf(0L) }
    var end by remember(item.id) { mutableLongStateOf(item.durationSec ?: 0L) }
    var active by remember { mutableStateOf("start") }
    var busy by remember { mutableStateOf(false) }
    var percent by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = !busy, onBack = onClose)

    LaunchedEffect(item.id) {
        val real = Trimmer.durationOf(item.path)
        if (real != null && real > 0) {
            duration = real
            if (end == 0L || end > real) end = real
        }
        if (duration > 0) frames = Trimmer.frames(item.path, 8, duration)
    }

    fun setStart(v: Long) { start = v.coerceIn(0, (end - 1).coerceAtLeast(0)); active = "start" }
    fun setEnd(v: Long) { end = v.coerceIn(start + 1, duration.coerceAtLeast(start + 1)); active = "end" }

    Column(
        Modifier.fillMaxSize().pointerInput(Unit) {}.background(MaterialTheme.colorScheme.background)
            .statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("قصّ مقطع", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                Text(item.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(12.dp))
            RoundIconButton(Icons.Rounded.Close, "إغلاق", onClose, enabled = !busy)
        }
        Spacer(Modifier.height(18.dp))

        if (duration <= 1) {
            Text("تعذّرت قراءة مدة هذا الفيديو، فلا يمكن قصّه.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }

        // الزمن يسير من اليسار إلى اليمين كما في كل المحرّرات.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Strip(frames, duration, start, end, enabled = !busy, onStart = ::setStart, onEnd = ::setEnd)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("0:00", style = MaterialTheme.typography.labelSmall.merge(NumberStyle), color = AppTheme.extra.dim)
                Spacer(Modifier.weight(1f))
                Text("المدة ${Fmt.clock(end - start)}", style = MaterialTheme.typography.labelSmall.merge(NumberStyle), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(Fmt.clock(duration), style = MaterialTheme.typography.labelSmall.merge(NumberStyle), color = AppTheme.extra.dim)
            }
        }
        Text(
            "اسحب المقبضين لتحديد المقطع، ثم اضبطه بالثانية من الأزرار.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 14.dp),
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // الترتيب يطابق الشريط: البداية يساراً والنهاية يميناً.
            Edge("النهاية", end, active == "end", !busy, { active = "end" }, { setEnd(end - 1) }, { setEnd(end + 1) }, Modifier.weight(1f))
            Edge("البداية", start, active == "start", !busy, { active = "start" }, { setStart(start - 1) }, { setStart(start + 1) }, Modifier.weight(1f))
        }
        error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 14.dp))
        }
        Spacer(Modifier.height(22.dp))
        GoldButton(
            "قصّ المقطع", {
                busy = true
                percent = 0
                error = null
                scope.launch {
                    try {
                        onDone(Trimmer.cut(context.applicationContext, item, start, end) { f -> percent = (f * 100).toInt() })
                    } catch (e: AppError) {
                        error = e.detail ?: "تعذّر قصّ المقطع"
                    } catch (t: Throwable) {
                        error = "تعذّر قصّ المقطع"
                    } finally {
                        busy = false
                    }
                }
            },
            Modifier.fillMaxWidth(), loading = busy, icon = Icons.Rounded.ContentCut,
            caption = if (busy) "جارٍ القصّ… ${Fmt.iso("$percent%")}" else "المدة ${Fmt.clock(end - start)}",
        )
        Text(
            "القصّ دقيق بالثانية، وقد يستغرق لحظات في المقاطع الطويلة. الفيديو الأصلي يبقى كما هو.",
            style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.dim, modifier = Modifier.padding(top = 12.dp, bottom = 20.dp),
        )
    }
}

@Composable
private fun Strip(frames: List<Bitmap>, duration: Long, start: Long, end: Long, enabled: Boolean, onStart: (Long) -> Unit, onEnd: (Long) -> Unit) {
    val gold = AppTheme.extra.gold
    val scrim = MaterialTheme.colorScheme.background.copy(alpha = 0.72f)
    // القيم الحالية تُقرأ داخل معالج السحب دون إعادة إنشائه.
    val latest = remember { LongArray(3) }
    latest[0] = start; latest[1] = end; latest[2] = duration
    var dragging by remember { mutableStateOf<String?>(null) }

    Box(
        Modifier.fillMaxWidth().height(68.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                fun timeAt(x: Float) = (x / size.width * latest[2]).toLong().coerceIn(0, latest[2])
                fun nearest(x: Float): String {
                    val xs = latest[0].toFloat() / latest[2] * size.width
                    val xe = latest[1].toFloat() / latest[2] * size.width
                    return if (abs(x - xs) <= abs(x - xe)) "start" else "end"
                }
                detectDragGestures(
                    onDragStart = { o -> dragging = nearest(o.x) },
                    onDragEnd = { dragging = null }, onDragCancel = { dragging = null },
                ) { change, _ ->
                    change.consume()
                    if (dragging == "start") onStart(timeAt(change.position.x)) else onEnd(timeAt(change.position.x))
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { o ->
                    val t = (o.x / size.width * latest[2]).toLong().coerceIn(0, latest[2])
                    val xs = latest[0].toFloat() / latest[2] * size.width
                    val xe = latest[1].toFloat() / latest[2] * size.width
                    if (abs(o.x - xs) <= abs(o.x - xe)) onStart(t) else onEnd(t)
                }
            },
    ) {
        Row(Modifier.fillMaxSize()) {
            frames.forEach { b ->
                Image(b.asImageBitmap(), null, Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val xs = start.toFloat() / duration * size.width
            val xe = end.toFloat() / duration * size.width
            drawRect(scrim, Offset.Zero, Size(xs, size.height))
            drawRect(scrim, Offset(xe, 0f), Size(size.width - xe, size.height))
            val bw = 3.dp.toPx()
            drawRoundRect(gold, Offset(xs + bw / 2, bw / 2), Size((xe - xs - bw).coerceAtLeast(0f), size.height - bw), CornerRadius(10.dp.toPx()), style = Stroke(bw))
            // المقبضان
            val hw = 12.dp.toPx()
            val grip = Size(2.5.dp.toPx(), size.height * 0.34f)
            for (x in listOf(xs, xe - hw)) {
                val left = x.coerceIn(0f, size.width - hw)
                drawRoundRect(gold, Offset(left, 0f), Size(hw, size.height), CornerRadius(6.dp.toPx()))
                drawRoundRect(Color(0xFF1B1305), Offset(left + (hw - grip.width) / 2, (size.height - grip.height) / 2), grip, CornerRadius(2.dp.toPx()))
            }
        }
    }
}

@Composable
private fun Edge(
    label: String, value: Long, selected: Boolean, enabled: Boolean,
    onSelect: () -> Unit, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clip(MaterialTheme.shapes.large).clickable(enabled = enabled, onClick = onSelect),
        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) AppTheme.extra.gold.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = AppTheme.extra.dim)
            Text(Fmt.clock(value), style = MaterialTheme.typography.headlineSmall.merge(NumberStyle), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(vertical = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RoundIconButton(Icons.Rounded.Remove, "أنقص ثانية", onMinus, Modifier.size(40.dp), enabled = enabled)
                RoundIconButton(Icons.Rounded.Add, "زد ثانية", onPlus, Modifier.size(40.dp), enabled = enabled)
            }
        }
    }
}
