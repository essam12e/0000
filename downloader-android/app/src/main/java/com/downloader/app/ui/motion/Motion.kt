package com.downloader.app.ui.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/*
 * لغة الحركة في التطبيق: منحنيات على طريقة GSAP (expo / back / elastic)، دخول متدرّج ثلاثي الأبعاد،
 * وميلان حقيقي مع حركة الجوال. كلها منفّذة بأدوات أندرويد نفسها (GSAP مكتبة ويب ولا تعمل داخل تطبيق أصلي).
 */

/** expo.out: انطلاق سريع ثم هبوط ناعم طويل. */
val ExpoOut = Easing { t -> if (t >= 1f) 1f else 1f - 2f.pow(-10f * t) }

/** back.out: يتجاوز الهدف قليلاً ثم يعود إليه. */
fun backOut(overshoot: Float = 1.7f) = Easing { t ->
    val p = t - 1f
    p * p * ((overshoot + 1f) * p + overshoot) + 1f
}

/** elastic.out: ارتداد مطاطي يخمد بسرعة. */
val ElasticOut = Easing { t ->
    if (t <= 0f) 0f else if (t >= 1f) 1f
    else (2f.pow(-10f * t) * sin((t - 0.075f) * (2 * PI) / 0.3) + 1).toFloat()
}

/**
 * ميلان الجوال من حسّاس الجاذبية: قيمة بين -1 و 1 لكل محور (x: يمين/يسار، y: أمام/خلف) بالنسبة لوضعية الإمساك المعتادة.
 * يعمل فقط والشاشة ظاهرة، ولا يحتاج أي صلاحية. يعيد صفراً إن لم يوجد حسّاس.
 */
@Composable
fun rememberTilt(): State<Offset> {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val tilt = remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(owner) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var fx = 0f
        var fy = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                // x: ميلان جانبي. y: نطرح وضعية الإمساك المعتادة (الجوال مائل نحو الوجه) لتكون نقطة الصفر مريحة.
                val x = (-e.values[0] / 5.5f).coerceIn(-1f, 1f)
                val y = ((e.values[1] - 6.2f) / 5.5f).coerceIn(-1f, 1f)
                fx += (x - fx) * 0.12f
                fy += (y - fy) * 0.12f
                tilt.value = Offset(fx, fy)
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        val observer = LifecycleEventObserver { _, event ->
            if (sensor == null) return@LifecycleEventObserver
            if (event == Lifecycle.Event.ON_RESUME) manager?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
            if (event == Lifecycle.Event.ON_PAUSE) manager?.unregisterListener(listener)
        }
        owner.lifecycle.addObserver(observer)
        if (sensor != null && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            manager?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose {
            owner.lifecycle.removeObserver(observer)
            manager?.unregisterListener(listener)
        }
    }
    return tilt
}

/**
 * دخول ثلاثي الأبعاد متدرّج: العنصر ينقلب من الأسفل (حول حافته السفلية) ويرتفع إلى مكانه مع تجاوز خفيف،
 * وكل عنصر يتأخر عن سابقه قليلاً — مثل stagger في GSAP.
 */
fun Modifier.enter3d(index: Int = 0, key: Any? = Unit, fromDegrees: Float = -62f, stepMs: Int = 65): Modifier = composed {
    val p = remember(key) { Animatable(0f) }
    val rise = with(LocalDensity.current) { 34.dp.toPx() }
    val camera = with(LocalDensity.current) { 16.dp.toPx() } * 60f
    LaunchedEffect(key) {
        delay((index.coerceIn(0, 12) * stepMs).toLong())
        p.animateTo(1f, tween(620, easing = backOut(1.35f)))
    }
    graphicsLayer {
        val v = p.value
        alpha = (v * 1.6f).coerceIn(0f, 1f)
        rotationX = fromDegrees * (1f - v)
        translationY = rise * (1f - v)
        transformOrigin = TransformOrigin(0.5f, 1f)
        cameraDistance = camera
    }
}

/** عمق مع ميلان الجوال: العنصر يدور قليلاً في الفضاء وينزاح بمقدار [depth] ليعطي إحساس الطبقات. */
fun Modifier.parallax(tilt: State<Offset>, degrees: Float = 7f, depth: Float = 0f): Modifier = composed {
    val shift = with(LocalDensity.current) { depth.dp.toPx() }
    val camera = with(LocalDensity.current) { 16.dp.toPx() } * 70f
    graphicsLayer {
        val t = tilt.value
        rotationY = t.x * degrees
        rotationX = -t.y * degrees
        translationX = t.x * shift
        translationY = t.y * shift
        cameraDistance = camera
    }
}

/** بطاقة تميل نحو الإصبع أثناء اللمس ثم ترتدّ بنابض عند رفعه. */
fun Modifier.pressTilt(degrees: Float = 9f): Modifier = composed {
    val scope = rememberCoroutineScope()
    val rx = remember { Animatable(0f) }
    val ry = remember { Animatable(0f) }
    val camera = with(LocalDensity.current) { 16.dp.toPx() } * 55f
    val back = spring<Float>(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            fun aim(pos: Offset) {
                val nx = (pos.x / size.width - 0.5f).coerceIn(-0.5f, 0.5f) * 2f
                val ny = (pos.y / size.height - 0.5f).coerceIn(-0.5f, 0.5f) * 2f
                scope.launch { ry.animateTo(nx * degrees, tween(120)) }
                scope.launch { rx.animateTo(-ny * degrees, tween(120)) }
            }
            aim(down.position)
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull() ?: break
                if (!change.pressed) break
                aim(change.position)
            }
            scope.launch { ry.animateTo(0f, back) }
            scope.launch { rx.animateTo(0f, back) }
        }
    }.graphicsLayer {
        rotationX = rx.value
        rotationY = ry.value
        cameraDistance = camera
    }
}
