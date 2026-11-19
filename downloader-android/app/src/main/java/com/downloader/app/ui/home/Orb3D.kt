package com.downloader.app.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.downloader.app.ui.motion.ElasticOut
import com.downloader.app.ui.motion.ExpoOut
import com.downloader.app.ui.motion.backOut
import com.downloader.app.ui.theme.AppTheme
import com.downloader.app.ui.theme.NumberStyle
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** لون النص فوق الكرة الداكنة (ثابت في المظهرين لأن الكرة داكنة دائماً). */
private val OnOrb = Color(0xFFEFE9DC)

/** مدار حول الكرة: نصف قطره نسبةً إلى الكرة، ميله عن خط النظر، دورانه في مستوى الشاشة، وسرعة قمره. */
private class Orbit(val radius: Float, val inclineDeg: Float, val rollDeg: Float, val speed: Float, val phase0: Float)

private val Orbits = listOf(
    Orbit(1.36f, 72f, -24f, 1.00f, 0.4f),
    Orbit(1.62f, 66f, 38f, -0.62f, 2.6f),
)

/**
 * الكرة ثلاثية الأبعاد: مجسّم مضاء من أعلى اليسار، حوله مداران ذهبيان مائلان يدور على كل منهما «قمر» صغير،
 * ويمرّ نصف المدار خلف الكرة ونصفه أمامها. المشهد كله يميل مع ميلان الجوال، والكرة تنضغط وتطلق موجة عند اللمس.
 *
 * [progress]: null في السكون، قيمة سالبة = تقدّم غير محدد (المداران يسرعان)، وإلا نسبة من 0 إلى 1 على حلقة التقدّم.
 */
@Composable
fun Orb(progress: Float?, paused: Boolean, enabled: Boolean, tilt: State<Offset>, onClick: () -> Unit) {
    val extra = AppTheme.extra
    val track = MaterialTheme.colorScheme.outline
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium), label = "press")
    val shown by animateFloatAsState((progress ?: 0f).coerceIn(0f, 1f), tween(350), label = "progress")
    val ring by animateFloatAsState(if (progress != null) 1f else 0f, tween(450, easing = ExpoOut), label = "ring")
    val idle = progress == null
    val busy = progress != null && progress < 0f

    // الدخول أول مرة: الكرة تأتي من العمق وتستقر بارتداد.
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(1100, easing = backOut(1.5f))) }
    // موجة تنطلق من الكرة عند الضغط.
    val wave = remember { Animatable(1f) }
    // دوران المدارين: نجمع الزاوية إطاراً بإطار حتى تتغير السرعة بنعومة بين السكون والتحليل والتنزيل.
    val angle = remember { mutableFloatStateOf(0f) }
    val speed by animateFloatAsState(if (busy) 5.5f else if (!idle && !paused) 2.4f else if (paused) 0.25f else 1f, tween(900, easing = ExpoOut), label = "speed")
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameNanos { now ->
            if (last != 0L) angle.floatValue += (now - last) / 1e9f * 0.55f * speed
            last = now
        }
    }
    val infinite = rememberInfiniteTransition(label = "orb")
    val bob by infinite.animateFloat(-1f, 1f, infiniteRepeatable(tween(1500, easing = androidx.compose.animation.core.FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    val spin by infinite.animateFloat(0f, 360f, infiniteRepeatable(tween(1300, easing = androidx.compose.animation.core.LinearEasing)), label = "spin")
    val camera = with(LocalDensity.current) { 16.dp.toPx() } * 42f

    Box(Modifier.size(290.dp), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.fillMaxSize().graphicsLayer {
                val e = enter.value
                val t = tilt.value
                val s = (0.25f + 0.75f * e) * press
                scaleX = s; scaleY = s
                alpha = (e * 1.8f).coerceIn(0f, 1f)
                rotationX = 62f * (1f - e) - t.y * 13f
                rotationY = t.x * 13f
                cameraDistance = camera
            }
        ) {
            val t = tilt.value
            val c = center
            val r = size.minDimension * 0.262f
            // هالة خلف الكرة تنزاح عكس الميلان (أبعد طبقة)
            val haloC = Offset(c.x - t.x * r * 0.22f, c.y - t.y * r * 0.22f)
            drawCircle(Brush.radialGradient(listOf(extra.gold.copy(alpha = 0.20f), Color.Transparent), center = haloC, radius = r * 1.9f), radius = r * 1.9f, center = haloC)
            // موجة الضغط
            if (wave.value < 1f) drawCircle(extra.gold.copy(alpha = (1f - wave.value) * 0.7f), radius = r * (1f + wave.value * 1.15f), center = c, style = Stroke(2.5.dp.toPx() * (1f - wave.value) + 0.5f))

            orbits(c, r, angle.floatValue, t, front = false, gold = extra.gold, bright = extra.goldBright)
            // الكرة: موضع اللمعة يتبع الميلان كأن مصدر الضوء ثابت
            val light = Offset(c.x - r * (0.38f + t.x * 0.30f), c.y - r * (0.46f + t.y * 0.30f))
            drawCircle(Brush.radialGradient(listOf(Color(0xFF4C4C54), Color(0xFF1B1B1E), Color(0xFF050506)), center = light, radius = r * 1.8f), radius = r, center = c)
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), center = light, radius = r * 0.55f), radius = r * 0.55f, center = light)
            drawCircle(extra.gold.copy(alpha = 0.34f), radius = r, center = c, style = Stroke(1.dp.toPx()))
            orbits(c, r, angle.floatValue, t, front = true, gold = extra.gold, bright = extra.goldBright)

            // حلقة التقدّم (أقرب طبقة: تنزاح مع الميلان)
            if (ring > 0f) {
                val pc = Offset(c.x + t.x * r * 0.10f, c.y + t.y * r * 0.10f)
                val rr = r * (1.02f + 0.16f * ring)
                val stroke = 4.dp.toPx()
                drawCircle(track.copy(alpha = ring * 0.9f), radius = rr, center = pc, style = Stroke(1.5.dp.toPx()))
                val topLeft = Offset(pc.x - rr, pc.y - rr)
                val box = Size(rr * 2, rr * 2)
                val p = progress ?: 0f
                if (p < 0f) drawArc(extra.goldBrush, spin - 90f, 78f, false, topLeft, box, alpha = ring, style = Stroke(stroke, cap = StrokeCap.Round))
                else if (shown > 0.003f) {
                    drawArc(extra.goldBrush, -90f, 360f * shown, false, topLeft, box, alpha = if (paused) 0.45f else ring, style = Stroke(stroke, cap = StrokeCap.Round))
                    // رأس مضيء في نهاية القوس
                    val a = (-90f + 360f * shown) * PI.toFloat() / 180f
                    val head = Offset(pc.x + rr * cos(a), pc.y + rr * sin(a))
                    if (!paused) drawCircle(Brush.radialGradient(listOf(extra.goldBright.copy(alpha = 0.85f), Color.Transparent), center = head, radius = stroke * 3.2f), radius = stroke * 3.2f, center = head)
                }
            }
        }
        // منطقة اللمس: دائرة الكرة فقط
        Box(
            Modifier.size(170.dp).clip(CircleShape).clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClickLabel = "اضغط للتنزيل") {
                scope.launch { wave.snapTo(0f); wave.animateTo(1f, tween(900, easing = ExpoOut)) }
                onClick()
            },
        )
        val depth = with(LocalDensity.current) { 10.dp.toPx() }
        Box(
            Modifier.graphicsLayer {
                // المحتوى فوق الكرة أقرب للمشاهد: ينزاح مع الميلان أكثر من الكرة
                val t = tilt.value
                translationX = t.x * depth; translationY = t.y * depth
                val e = enter.value; scaleX = e * press; scaleY = e * press; alpha = e.coerceIn(0f, 1f)
            },
            contentAlignment = Alignment.Center,
        ) {
            if (idle) {
                val lift = with(LocalDensity.current) { 5.dp.toPx() }
                Icon(Icons.Rounded.ArrowDownward, null, tint = extra.gold.copy(alpha = 0.95f), modifier = Modifier.size(48.dp).graphicsLayer { translationY = bob * lift })
            } else if ((progress ?: -1f) >= 0f) {
                // النسبة «تنبض» بارتداد مطاطي عند ظهورها
                val pop = remember { Animatable(0.4f) }
                LaunchedEffect(Unit) { pop.animateTo(1f, tween(900, easing = ElasticOut)) }
                Row(Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }, verticalAlignment = Alignment.Bottom) {
                    Text("${(shown * 100).toInt()}", style = NumberStyle.copy(fontSize = 46.sp, lineHeight = 52.sp, fontWeight = FontWeight.ExtraBold), color = OnOrb)
                    Text("%", style = NumberStyle.copy(fontSize = 18.sp), color = OnOrb.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 9.dp, start = 3.dp))
                }
            }
        }
    }
}

/**
 * يرسم نصف كل مدار (الخلفي أو الأمامي) مع قمره: نحسب نقاط الدائرة في الفضاء، نُميلها، ثم نُسقطها بمنظور بسيط.
 * الجزء الخلفي يُرسم قبل الكرة وبشفافية أقل، فيبدو أنه يمرّ خلفها فعلاً.
 */
private fun DrawScope.orbits(c: Offset, r: Float, time: Float, tilt: Offset, front: Boolean, gold: Color, bright: Color) {
    val steps = 72
    val eye = r * 7f
    val ty = tilt.x * 0.30f      // دوران إضافي حول المحور الرأسي مع الميلان الجانبي
    val tx = tilt.y * 0.30f
    for (o in Orbits) {
        val inc = o.inclineDeg * PI.toFloat() / 180f
        val roll = o.rollDeg * PI.toFloat() / 180f
        val R = r * o.radius
        fun project(theta: Float): Triple<Float, Float, Float> {
            var x = R * cos(theta)
            var y = R * sin(theta)
            var z = 0f
            // إمالة حول المحور الأفقي
            val y1 = y * cos(inc + tx); z = y * sin(inc + tx); y = y1
            // دوران في مستوى الشاشة
            val x2 = x * cos(roll) - y * sin(roll); val y2 = x * sin(roll) + y * cos(roll); x = x2; y = y2
            // ميلان الجوال
            val x3 = x * cos(ty) + z * sin(ty); val z3 = -x * sin(ty) + z * cos(ty); x = x3; z = z3
            val k = eye / (eye - z)
            return Triple(c.x + x * k, c.y + y * k, z)
        }
        var prev = project(0f)
        for (i in 1..steps) {
            val cur = project(i * 2f * PI.toFloat() / steps)
            val z = (prev.third + cur.third) / 2f
            if ((z >= 0f) == front) {
                val depth = (z / R + 1f) / 2f                       // 0 = أبعد نقطة، 1 = أقرب نقطة
                drawLine(gold.copy(alpha = 0.10f + 0.62f * depth), Offset(prev.first, prev.second), Offset(cur.first, cur.second), strokeWidth = (0.7f + 1.5f * depth).dp.toPx(), cap = StrokeCap.Round)
            }
            prev = cur
        }
        val m = project(o.phase0 + time * o.speed)
        if ((m.third >= 0f) == front) {
            val depth = (m.third / R + 1f) / 2f
            val size = (2.6f + 3.4f * depth).dp.toPx()
            val at = Offset(m.first, m.second)
            drawCircle(Brush.radialGradient(listOf(bright.copy(alpha = 0.55f * (0.4f + 0.6f * depth)), Color.Transparent), center = at, radius = size * 3.4f), radius = size * 3.4f, center = at)
            drawCircle(bright.copy(alpha = 0.55f + 0.45f * depth), radius = size, center = at)
        }
    }
}
