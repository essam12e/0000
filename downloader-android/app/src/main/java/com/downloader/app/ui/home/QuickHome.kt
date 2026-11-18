package com.downloader.app.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.downloader.app.data.DownloadTask
import com.downloader.app.data.ErrorType
import com.downloader.app.data.Fmt
import com.downloader.app.data.Library
import com.downloader.app.data.LibraryItem
import com.downloader.app.data.MediaKind
import com.downloader.app.data.TaskState
import com.downloader.app.data.hint
import com.downloader.app.data.message
import com.downloader.app.engine.DownloadManager
import com.downloader.app.ui.components.GoldButton
import com.downloader.app.ui.components.QuietButton
import com.downloader.app.ui.components.RoundIconButton
import com.downloader.app.ui.components.Thumb
import com.downloader.app.ui.library.kindIcon
import com.downloader.app.ui.library.kindName
import com.downloader.app.ui.theme.AppTheme
import com.downloader.app.ui.theme.NumberStyle
import java.io.File

/** لون النص فوق الكرة الداكنة (ثابت في المظهرين لأن الكرة داكنة دائماً). */
private val OnOrb = Color(0xFFEFE9DC)

/**
 * الرئيسية: زر واحد كبير. ينسخ المستخدم الرابط ثم يضغط، فيُحلَّل الرابط ويُنزَّل بالجودة الافتراضية،
 * وتظهر حلقة التقدّم حول الكرة، ثم شاشة النتيجة (مشاركة / حفظ / قصّ).
 */
@Composable
fun QuickHome(
    vm: HomeViewModel,
    bottomInset: Dp,
    saving: Set<String>,
    onOpenOptions: () -> Unit,
    onOpenLogin: () -> Unit,
    onPlay: (LibraryItem) -> Unit,
    onShare: (LibraryItem) -> Unit,
    onSave: (LibraryItem) -> Unit,
    onTrim: (LibraryItem) -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val tasks by DownloadManager.tasks.collectAsStateWithLifecycle()
    val items by Library.items.collectAsStateWithLifecycle()
    val task = tasks.firstOrNull { it.id == ui.quickTaskId }
    val result = items.firstOrNull { it.id == ui.resultItemId }

    // المهمة أُلغيت من مكان آخر (مثلاً من المكتبة): نعود للوضع الأول.
    LaunchedEffect(ui.quickTaskId, task == null) {
        if (ui.quickTaskId != null && task == null) {
            kotlinx.coroutines.delay(600)
            if (DownloadManager.tasks.value.none { it.id == vm.ui.value.quickTaskId } && vm.ui.value.resultItemId == null) vm.quickReset()
        }
    }

    AnimatedContent(
        result, contentKey = { it?.id },
        transitionSpec = { (fadeIn(tween(320)) + scaleIn(tween(320), initialScale = 0.97f)) togetherWith fadeOut(tween(160)) },
        label = "home",
    ) { done ->
        if (done != null) {
            ResultView(
                done, bottomInset, saving = done.id in saving, onClose = vm::quickReset,
                onPlay = { onPlay(done) }, onShare = { onShare(done) }, onSave = { onSave(done) },
                onTrim = { onTrim(done) }, onOpenLibrary = { vm.quickReset(); onOpenLibrary() },
            )
        } else {
            val failed = (ui.state as? HomeState.Failed)?.takeIf { ui.quick }
            val analyzing = ui.quick && ui.state is HomeState.Loading
            val progress: Float? = when {
                // قبل وصول أول نسبة (مرحلة التحضير) تدور الحلقة بدل أن تقف على صفر.
                task != null && task.state == TaskState.RUNNING -> if (task.progress <= 0f) -1f else task.progress
                task != null && task.state == TaskState.QUEUED -> -1f
                task != null -> task.progress.coerceAtLeast(0f)
                analyzing || (ui.quick && !ui.quickChoice && ui.quickTaskId == null && failed == null && ui.state is HomeState.Ready) -> -1f
                else -> null
            }
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding()) {
            val viewport = maxHeight
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                RoundIconButton(Icons.Rounded.Tune, "خيارات التحميل", onOpenOptions, container = Color.Transparent)
            }
            // المحتوى في منتصف الشاشة، ويصير قابلاً للتمرير على الشاشات القصيرة.
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = viewport).padding(top = 56.dp, bottom = bottomInset),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Text("داونلودر", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    "DOWNLOADER", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 6.sp, fontWeight = FontWeight.Medium),
                    color = AppTheme.extra.dim, modifier = Modifier.padding(top = 2.dp),
                )
                Spacer(Modifier.height(44.dp))
                Orb(
                    progress = progress, paused = task != null && task.state != TaskState.RUNNING && task.state != TaskState.QUEUED,
                    enabled = progress == null && failed == null && !ui.quickChoice, onClick = { vm.quickStart() },
                )
                Spacer(Modifier.height(34.dp))
                val phase = when {
                    failed != null -> "error"
                    task != null && task.state == TaskState.FAILED -> "task-failed"
                    task != null && task.state == TaskState.PAUSED -> "paused"
                    task != null -> "downloading"
                    progress != null -> "analyzing"
                    ui.quickChoice -> "choice"
                    else -> "idle"
                }
                AnimatedContent(
                    phase, transitionSpec = { (fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 6 }) togetherWith fadeOut(tween(120)) },
                    label = "phase", modifier = Modifier.fillMaxWidth(),
                ) { p ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        when (p) {
                            "idle" -> Idle(ui.clipboardUrl)
                            "choice" -> Caption("اختر الجودة", "اضغط على الجودة التي تريدها ليبدأ التنزيل")
                            "analyzing" -> {
                                Caption("جارٍ التحليل…", "نقرأ الرابط ونختار أفضل جودة")
                                Spacer(Modifier.height(22.dp))
                                CancelLink { vm.quickCancel() }
                            }
                            "downloading" -> task?.let { Downloading(it) { vm.quickCancel() } }
                            "paused" -> {
                                Caption("التنزيل متوقف", task?.spec?.title.orEmpty())
                                Spacer(Modifier.height(20.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    QuietButton("استكمال", { task?.let { DownloadManager.resume(it.id) } }, Modifier.weight(1f))
                                    QuietButton("إلغاء", { vm.quickCancel() }, Modifier.weight(1f), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            "task-failed" -> {
                                Caption("تعذّر التنزيل", task?.error?.lineSequence()?.firstOrNull().orEmpty(), error = true)
                                Spacer(Modifier.height(20.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    QuietButton("إعادة المحاولة", { task?.let { DownloadManager.retry(it.id) } }, Modifier.weight(1f))
                                    QuietButton("إلغاء", { vm.quickCancel() }, Modifier.weight(1f), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            "error" -> failed?.let { f ->
                                Caption(f.type.message(), f.type.hint(), error = true)
                                Spacer(Modifier.height(20.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    QuietButton("حاول مجدداً", { vm.quickStart(ui.url.takeIf { it.isNotBlank() }) }, Modifier.weight(1f))
                                    if (f.type == ErrorType.PRIVATE) QuietButton("تسجيل الدخول", onOpenLogin, Modifier.weight(1f))
                                    else QuietButton("إغلاق", { vm.quickReset() }, Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            val choiceInfo = (ui.state as? HomeState.Ready)?.info?.takeIf { ui.quickChoice }
            androidx.compose.animation.AnimatedVisibility(choiceInfo != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(160))) {
                Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(remember { MutableInteractionSource() }, indication = null) { vm.quickCancel() })
            }
            androidx.compose.animation.AnimatedVisibility(
                choiceInfo != null, modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(),
                exit = androidx.compose.animation.slideOutVertically(tween(200)) { it } + fadeOut(tween(160)),
            ) {
                var last by remember { androidx.compose.runtime.mutableStateOf(choiceInfo) }
                if (choiceInfo != null) last = choiceInfo
                last?.let { info ->
                    androidx.activity.compose.BackHandler(enabled = choiceInfo != null) { vm.quickCancel() }
                    QualityPopup(
                        info, ui, bottomInset,
                        // اختيار الجودة يبدأ التنزيل مباشرة.
                        onSelect = { id -> vm.selectOption(id); vm.quickConfirm() }, onAudio = { vm.setAudioMode(true); vm.quickConfirm() },
                        onMore = vm::quickToOptions, onClose = vm::quickCancel,
                    )
                }
            }
            }
        }
    }
}

/** النافذة المنبثقة بعد تحليل الرابط: الجودات المتاحة مع أحجامها و«صوت فقط». الضغط على أي خيار يبدأ التنزيل. */
@Composable
private fun QualityPopup(
    info: com.downloader.app.data.MediaInfo, ui: HomeUi, bottomInset: Dp,
    onSelect: (String) -> Unit, onAudio: () -> Unit, onMore: () -> Unit, onClose: () -> Unit,
) {
    val selected = info.videoOptions.firstOrNull { it.id == ui.selectedOptionId } ?: info.videoOptions.firstOrNull()
    val audio = ui.audioMode
    Surface(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = bottomInset + 6.dp)
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
        shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), shadowElevation = 18.dp,
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Thumb(info.thumbnail, Modifier.size(width = 76.dp, height = 52.dp), fallback = kindIcon(MediaKind.VIDEO))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(info.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(info.platform, Fmt.duration(info.durationSec)).joinToString("  ·  ") { Fmt.iso(it) },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    )
                }
                Spacer(Modifier.width(8.dp))
                RoundIconButton(Icons.Rounded.Close, "إغلاق", onClose, Modifier.size(38.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("اختر الجودة — يبدأ التنزيل فوراً", style = MaterialTheme.typography.labelMedium, color = AppTheme.extra.dim)
            Spacer(Modifier.height(8.dp))
            Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                info.videoOptions.forEach { o ->
                    ChoiceRow(
                        title = o.label, badge = o.badge, note = o.detail,
                        size = if (o.sizeBytes != null) Fmt.bytes(o.sizeBytes) else null,
                        selected = false, onClick = { onSelect(o.id) },
                    )
                }
                if (info.hasAudio) {
                    ChoiceRow(
                        title = "صوت فقط", badge = ui.audioFormat.uppercase(), note = "بدون فيديو",
                        size = info.audioSizeBytes?.let { Fmt.bytes(it) }, selected = false, onClick = onAudio,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "خيارات أكثر: ترجمة، قصّ، لغة الصوت", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clip(CircleShape).clickable(onClick = onMore).padding(vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun ChoiceRow(title: String, badge: String?, note: String, size: String?, selected: Boolean, onClick: () -> Unit) {
    val gold = AppTheme.extra.gold
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .background(if (selected) gold.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(Modifier.border(if (selected) 1.5.dp else 1.dp, if (selected) gold.copy(alpha = 0.85f) else MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp).background(gold.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.ArrowDownward, null, tint = gold, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Fmt.iso(title), style = MaterialTheme.typography.labelLarge.merge(NumberStyle), color = MaterialTheme.colorScheme.onSurface)
                if (!badge.isNullOrBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        badge, style = MaterialTheme.typography.labelSmall, color = gold,
                        modifier = Modifier.border(1.dp, gold.copy(alpha = 0.5f), MaterialTheme.shapes.extraSmall).padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
            if (note.isNotBlank()) Text(Fmt.iso(note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        if (size != null) Text(Fmt.iso(size), style = MaterialTheme.typography.labelMedium.merge(NumberStyle), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Caption(title: String, body: String, error: Boolean = false) {
    Text(
        title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
    )
    if (body.isNotBlank()) {
        Text(
            body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun CancelLink(onClick: () -> Unit) {
    Text(
        "إلغاء", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error,
        modifier = Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 28.dp, vertical = 10.dp),
    )
}

@Composable
private fun Idle(clipboardUrl: String?) {
    Caption("اضغط للتنزيل", "انسخ رابط الفيديو أو الصورة، ثم اضغط على الدائرة")
    Spacer(Modifier.height(22.dp))
    if (clipboardUrl != null) {
        val host = runCatching { android.net.Uri.parse(clipboardUrl).host?.removePrefix("www.") }.getOrNull() ?: "رابط"
        Surface(
            shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, AppTheme.extra.gold.copy(alpha = 0.45f)),
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(AppTheme.extra.gold, CircleShape))
                Spacer(Modifier.width(9.dp))
                Text("رابط منسوخ جاهز  ·  ${Fmt.iso(host)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            }
        }
    } else {
        Text(
            "يوتيوب  ·  انستجرام  ·  تيك توك  ·  وأكثر", style = MaterialTheme.typography.labelSmall,
            color = AppTheme.extra.dim, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Downloading(task: DownloadTask, onCancel: () -> Unit) {
    Text(
        task.spec.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
    )
    Text(
        task.stage?.takeIf { it.isNotBlank() } ?: "جارٍ التنزيل…", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
    )
    Spacer(Modifier.height(22.dp))
    Surface(
        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(vertical = 14.dp)) {
            Stat("السرعة", Fmt.speed(task.speed) ?: task.speed?.takeIf { it.isNotBlank() } ?: "—", Modifier.weight(1f))
            Stat("المتبقي", Fmt.eta(task.etaSec)?.removePrefix("باقي ") ?: "—", Modifier.weight(1f))
            Stat("الحجم", Fmt.bytes(task.spec.expectedBytes), Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(14.dp))
    CancelLink(onCancel)
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = AppTheme.extra.dim)
        Text(Fmt.iso(value), style = MaterialTheme.typography.labelLarge.merge(NumberStyle), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
    }
}

/**
 * الكرة: [progress] = null في السكون، قيمة سالبة = تقدّم غير محدد (حلقة تدور)، وإلا نسبة من 0 إلى 1.
 */
@Composable
private fun Orb(progress: Float?, paused: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val extra = AppTheme.extra
    val track = MaterialTheme.colorScheme.outline
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), label = "press")
    val infinite = rememberInfiniteTransition(label = "orb")
    val breathe by infinite.animateFloat(1f, 1.035f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
    val spin by infinite.animateFloat(0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "spin")
    val shown by animateFloatAsState((progress ?: 0f).coerceIn(0f, 1f), tween(350), label = "progress")
    val ring by animateFloatAsState(if (progress != null) 1f else 0f, tween(400), label = "ring")
    val idle = progress == null

    Box(
        Modifier.size(248.dp).clip(CircleShape)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClickLabel = "اضغط للتنزيل", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { val s = press * if (idle) breathe else 1f; scaleX = s; scaleY = s }) {
            val c = center
            val r = size.minDimension * 0.355f
            // هالة ذهبية خافتة جداً
            drawCircle(Brush.radialGradient(listOf(extra.gold.copy(alpha = 0.16f), Color.Transparent), center = c, radius = size.minDimension * 0.5f), radius = size.minDimension * 0.5f)
            // الكرة: إضاءة من أعلى اليسار
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFF45454C), Color(0xFF1B1B1E), Color(0xFF060607)),
                    center = Offset(c.x - r * 0.38f, c.y - r * 0.46f), radius = r * 1.75f,
                ),
                radius = r, center = c,
            )
            drawCircle(extra.gold.copy(alpha = 0.28f), radius = r, center = c, style = Stroke(1.dp.toPx()))
            // حلقة التقدّم
            if (ring > 0f) {
                val rr = size.minDimension * 0.455f
                val stroke = 3.5.dp.toPx()
                drawCircle(track.copy(alpha = ring), radius = rr, center = c, style = Stroke(1.5.dp.toPx()))
                val topLeft = Offset(c.x - rr, c.y - rr)
                val arc = Size(rr * 2, rr * 2)
                val p = progress ?: 0f
                if (p < 0f) drawArc(extra.goldBrush, startAngle = spin - 90f, sweepAngle = 80f, useCenter = false, topLeft = topLeft, size = arc, style = Stroke(stroke, cap = StrokeCap.Round), alpha = ring)
                else if (shown > 0.003f) drawArc(extra.goldBrush, startAngle = -90f, sweepAngle = 360f * shown, useCenter = false, topLeft = topLeft, size = arc, style = Stroke(stroke, cap = StrokeCap.Round), alpha = if (paused) 0.45f else ring)
            }
        }
        if (idle) {
            Icon(Icons.Rounded.ArrowDownward, null, tint = extra.gold.copy(alpha = 0.92f), modifier = Modifier.size(46.dp))
        } else if ((progress ?: -1f) >= 0f) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${(shown * 100).toInt()}", style = NumberStyle.copy(fontSize = 46.sp, lineHeight = 52.sp, fontWeight = FontWeight.ExtraBold), color = OnOrb)
                Text("%", style = NumberStyle.copy(fontSize = 18.sp), color = OnOrb.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 9.dp, start = 3.dp))
            }
        }
    }
}

/** شاشة النتيجة بعد اكتمال التنزيل أو القصّ. */
@Composable
private fun ResultView(
    item: LibraryItem, bottomInset: Dp, saving: Boolean, onClose: () -> Unit,
    onPlay: () -> Unit, onShare: () -> Unit, onSave: () -> Unit, onTrim: () -> Unit, onOpenLibrary: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(start = 22.dp, end = 22.dp, bottom = bottomInset + 8.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(22.dp).background(AppTheme.extra.success.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, null, tint = AppTheme.extra.success, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(9.dp))
            Text(if (item.clip) "تم القصّ" else "تم التنزيل", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
            RoundIconButton(Icons.Rounded.Close, "إغلاق", onClose)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(MaterialTheme.shapes.large).clickable(onClick = onPlay), contentAlignment = Alignment.Center) {
            val model: Any? = when (item.kind) {
                MediaKind.IMAGE, MediaKind.VIDEO -> File(item.path)
                else -> item.thumbUrl
            }
            Thumb(model, Modifier.fillMaxSize(), fallback = kindIcon(item.kind), shape = MaterialTheme.shapes.large)
            if (item.kind == MediaKind.VIDEO || item.kind == MediaKind.AUDIO) {
                Box(Modifier.size(58.dp).background(Color(0x99000000), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayArrow, "تشغيل", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(item.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(kindName(item.kind), Fmt.bytes(item.size), Fmt.duration(item.durationSec)).joinToString("  ·  ") { Fmt.iso(it) },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            "أُضيف إلى مكتبتك. يمكنك مشاركته أو حفظه في الجهاز.", style = MaterialTheme.typography.bodySmall,
            color = AppTheme.extra.dim, modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.height(22.dp))
        GoldButton("مشاركة", onShare, Modifier.fillMaxWidth(), icon = Icons.Rounded.Share)
        Spacer(Modifier.height(10.dp))
        QuietButton("حفظ في الجهاز", onSave, Modifier.fillMaxWidth(), icon = Icons.Rounded.SaveAlt, loading = saving)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (item.kind == MediaKind.VIDEO) QuietButton(if (item.clip) "قصّ مقطعاً آخر" else "قصّ مقطع", onTrim, Modifier.weight(1f), icon = Icons.Rounded.ContentCut, compact = true)
            QuietButton("عرض في المكتبة", onOpenLibrary, Modifier.weight(1f), icon = Icons.Rounded.Layers, compact = true)
        }
    }
}
