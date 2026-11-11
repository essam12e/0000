package com.downloader.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.downloader.app.ui.theme.AppTheme

/** علامة التطبيق: سهم تحميل ذهبي فوق صينية — مرسومة بالكود لتطابق الأيقونة. */
@Composable
fun BrandMark(size: Dp, modifier: Modifier = Modifier, brush: Brush = AppTheme.extra.goldBrush) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val arrow = Path().apply {
            moveTo(w * 0.38f, h * 0.06f); lineTo(w * 0.62f, h * 0.06f)
            lineTo(w * 0.62f, h * 0.36f); lineTo(w * 0.80f, h * 0.36f)
            lineTo(w * 0.50f, h * 0.70f); lineTo(w * 0.20f, h * 0.36f)
            lineTo(w * 0.38f, h * 0.36f); close()
        }
        drawPath(arrow, brush)
        val tray = Path().apply {
            moveTo(w * 0.12f, h * 0.58f)
            lineTo(w * 0.12f, h * 0.84f)
            lineTo(w * 0.88f, h * 0.84f)
            lineTo(w * 0.88f, h * 0.58f)
        }
        drawPath(tray, brush, style = Stroke(width = w * 0.11f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

/** الزر الذهبي — الفعل الأساسي الوحيد في كل شاشة. */
@Composable
fun GoldButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    caption: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "press")
    val extra = AppTheme.extra
    val active = enabled && !loading
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.38f }
            .heightIn(min = 56.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(extra.goldBrush)
            .clickable(interaction, indication = null, enabled = active, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(20.dp), color = extra.onGold, strokeWidth = 2.5.dp)
            } else if (icon != null) {
                Icon(icon, null, tint = extra.onGold, modifier = Modifier.size(22.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text, style = MaterialTheme.typography.labelLarge, color = extra.onGold, maxLines = 1)
                if (caption != null) {
                    Text(caption, style = MaterialTheme.typography.labelSmall, color = extra.onGold.copy(alpha = 0.72f), maxLines = 1)
                }
            }
        }
    }
}

/** زر محايد ثانوي. */
@Composable
fun QuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    compact: Boolean = false,
) {
    Surface(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.heightIn(min = if (compact) 44.dp else 56.dp),
        shape = if (compact) MaterialTheme.shapes.small else MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            Modifier.padding(horizontal = if (compact) 14.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(16.dp), color = tint, strokeWidth = 2.dp)
            else if (icon != null) Icon(icon, null, tint = tint.copy(alpha = if (enabled) 1f else 0.4f), modifier = Modifier.size(if (compact) 18.dp else 20.dp))
            Text(
                text,
                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                color = tint.copy(alpha = if (enabled) 1f else 0.4f), maxLines = 1,
            )
        }
    }
}

@Composable
fun RoundIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick, enabled = enabled, modifier = modifier.size(44.dp), shape = CircleShape, color = container,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, description, tint = tint.copy(alpha = if (enabled) 1f else 0.35f), modifier = Modifier.size(20.dp))
        }
    }
}

/** شريط تقدّم ذهبي متحرك: امتلاء نابض + لمعة تنساب عليه. */
@Composable
fun GoldProgress(progress: Float, modifier: Modifier = Modifier, active: Boolean = true, height: Dp = 6.dp) {
    val extra = AppTheme.extra
    val indeterminate = progress < 0f
    val target = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(target, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow), label = "progress")
    val transition = rememberInfiniteTransition(label = "sheen")
    val sheen by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "sheen")
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        val rtl = layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl
        if (indeterminate) {
            val segW = size.width * 0.32f
            val x = (size.width + segW) * sheen - segW
            val left = if (rtl) size.width - x - segW else x
            drawRoundRect(extra.gold, topLeft = Offset(left.coerceIn(0f, size.width - 1f), 0f),
                size = Size(segW.coerceAtMost(size.width - left.coerceAtLeast(0f)), size.height), cornerRadius = r)
        } else if (animated > 0f) {
            val fillW = (size.width * animated).coerceAtLeast(size.height)
            val left = if (rtl) size.width - fillW else 0f
            drawRoundRect(
                Brush.horizontalGradient(listOf(extra.goldDeep, extra.gold, extra.goldBright).let { if (rtl) it.reversed() else it }, left, left + fillW),
                topLeft = Offset(left, 0f), size = Size(fillW, size.height), cornerRadius = r,
            )
            if (active) {
                val sw = fillW * 0.35f
                val sx = left + (fillW + sw) * (if (rtl) 1f - sheen else sheen) - sw
                val a = maxOf(sx, left)
                val b = minOf(sx + sw, left + fillW)
                if (b > a) drawRoundRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.38f), Color.Transparent), sx, sx + sw),
                    topLeft = Offset(a, 0f), size = Size(b - a, size.height), cornerRadius = r,
                )
            }
        }
    }
}

/** هيكل وميض أثناء التحميل. */
@Composable
fun Shimmer(modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.small) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(-1f, 2f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "x")
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val glow = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        modifier.clip(shape).drawBehind {
            drawRect(base)
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, glow, Color.Transparent), size.width * (x - 0.5f), size.width * (x + 0.5f)))
        }
    )
}

@Composable
fun Thumb(
    model: Any?,
    modifier: Modifier = Modifier,
    fallback: ImageVector,
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.small,
    contentScale: ContentScale = ContentScale.Crop,
) {
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        Icon(fallback, null, tint = AppTheme.extra.dim, modifier = Modifier.size(26.dp))
        if (model != null) {
            AsyncImage(model = model, contentDescription = null, contentScale = contentScale, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun Badge(text: String, modifier: Modifier = Modifier, gold: Boolean = false) {
    val extra = AppTheme.extra
    Text(
        text,
        modifier = modifier
            .border(1.dp, if (gold) extra.gold.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 6.dp, vertical = 1.dp),
        style = MaterialTheme.typography.labelSmall,
        color = if (gold) extra.gold else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 14.dp, height = 2.dp).background(AppTheme.extra.gold.copy(alpha = 0.8f), RoundedCornerShape(1.dp)))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelSmall, color = AppTheme.extra.dim)
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, icon: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().padding(horizontal = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(112.dp).drawBehind {
                drawCircle(Brush.radialGradient(listOf(Color(0x33D9B36A), Color.Transparent)), radius = size.minDimension * 0.8f)
                drawCircle(Color(0xFF2E2D2A), style = Stroke(1.dp.toPx()))
            },
            contentAlignment = Alignment.Center,
        ) { icon() }
        Spacer(Modifier.height(22.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** حوار مخصّص بنفس هوية التطبيق. */
@Composable
fun AppDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    dismissText: String = "إلغاء",
    danger: Boolean = false,
    content: @Composable () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(Modifier.padding(22.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(10.dp))
                content()
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (confirmText != null && onConfirm != null) {
                        if (danger) QuietButton(confirmText, onConfirm, Modifier.weight(1f), tint = MaterialTheme.colorScheme.error, compact = true)
                        else GoldButton(confirmText, onConfirm, Modifier.weight(1f).heightIn(min = 44.dp))
                    }
                    QuietButton(dismissText, onDismiss, Modifier.weight(1f), compact = true)
                }
            }
        }
    }
}

@Composable
fun OneLine(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
