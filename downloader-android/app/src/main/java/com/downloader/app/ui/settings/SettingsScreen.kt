package com.downloader.app.ui.settings

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.downloader.app.BuildConfig
import com.downloader.app.R
import com.downloader.app.data.Fmt
import com.downloader.app.data.qualityChoices
import com.downloader.app.ui.components.AppDialog
import com.downloader.app.ui.components.SectionLabel
import com.downloader.app.ui.theme.AppTheme
import com.downloader.app.ui.theme.NumberStyle

@Composable
fun SettingsScreen(vm: SettingsViewModel, bottomInset: Dp, onOpenLogin: () -> Unit, modifier: Modifier = Modifier) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val loggedIn by vm.loggedIn.collectAsStateWithLifecycle()
    val version by vm.engineVersion.collectAsStateWithLifecycle()
    val updating by vm.updating.collectAsStateWithLifecycle()
    val cache by vm.cacheBytes.collectAsStateWithLifecycle()
    var qualityDialog by remember { mutableStateOf(false) }
    var aboutDialog by remember { mutableStateOf(false) }
    var logoutDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.refresh() }

    Column(
        modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = bottomInset + 16.dp),
    ) {
        Text("الإعدادات", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(20.dp))

        SectionLabel("التحميل")
        Group {
            SettingRow("الجودة الافتراضية", "تُحدَّد تلقائياً بعد تحليل الرابط", onClick = { qualityDialog = true }) {
                Text(qualityChoices.first { it.first == s.defaultQuality }.second, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null, tint = AppTheme.extra.dim, modifier = Modifier.size(20.dp))
            }
            Divider()
            SettingRow("صيغة الصوت", "عند اختيار «صوت فقط»") {
                Segmented(listOf("mp3" to "MP3", "m4a" to "M4A"), s.audioFormat) { vm.setAudioFormat(it) }
            }
            Divider()
            SettingRow("التحميلات المتوازية", "عدد الملفات التي تُحمَّل في الوقت نفسه") {
                Stepper(s.maxParallel, 1, 5) { vm.setParallel(it) }
            }
        }

        Spacer(Modifier.height(22.dp))
        SectionLabel("المحرك")
        Group {
            SettingRow("محرك yt-dlp", "الإصدار ${version ?: "…"}\nحدّثه إذا توقف موقع عن العمل") {
                Pill(if (updating) "جاري التحديث" else "تحديث", loading = updating, onClick = vm::updateEngine)
            }
        }

        Spacer(Modifier.height(22.dp))
        SectionLabel("الحسابات")
        Group {
            SettingRow(
                "حساب انستجرام",
                if (loggedIn) "مسجّل الدخول — الكوكيز محفوظة على جهازك فقط" else "اختياري: للستوري والمحتوى الخاص. يُنصح بحساب ثانوي",
            ) {
                if (loggedIn) Pill("تسجيل الخروج", danger = true) { logoutDialog = true }
                else Pill("تسجيل الدخول", onClick = onOpenLogin)
            }
        }

        Spacer(Modifier.height(22.dp))
        SectionLabel("التخزين")
        Group {
            SettingRow("مسح الكاش", "ملفات مؤقتة وصور مصغّرة: ${Fmt.bytes(cache.coerceAtLeast(1024))}\nلا يمسّ ملفات «تحميلاتي»") {
                Pill("مسح", onClick = vm::clearCache)
            }
        }

        Spacer(Modifier.height(22.dp))
        SectionLabel("حول")
        Group {
            SettingRow("عن التطبيق", "داونلودر ${BuildConfig.VERSION_NAME}", onClick = { aboutDialog = true }) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null, tint = AppTheme.extra.dim, modifier = Modifier.size(20.dp))
            }
        }
    }

    if (qualityDialog) {
        AppDialog("الجودة الافتراضية", onDismiss = { qualityDialog = false }, dismissText = "إغلاق") {
            Column {
                qualityChoices.forEach { (value, label) ->
                    val on = value == s.defaultQuality
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(MaterialTheme.shapes.small)
                            .clickable { vm.setQuality(value); qualityDialog = false }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, style = MaterialTheme.typography.bodyLarge.merge(NumberStyle.copy(fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal)),
                            color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        if (on) Icon(Icons.Rounded.Check, null, tint = AppTheme.extra.gold, modifier = Modifier.size(20.dp))
                    }
                }
                Text("إن لم تتوفر الجودة المختارة يُحدَّد أقرب خيار أقل منها.", style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.dim, modifier = Modifier.padding(top = 8.dp, start = 8.dp))
            }
        }
    }
    if (logoutDialog) {
        AppDialog("تسجيل الخروج من انستجرام؟", onDismiss = { logoutDialog = false }, confirmText = "خروج ومسح الكوكيز", danger = true,
            onConfirm = { vm.logout(); logoutDialog = false }) {
            Text("ستُمسح الكوكيز وبيانات صفحة تسجيل الدخول من الجهاز نهائياً.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (aboutDialog) {
        AppDialog("عن داونلودر", onDismiss = { aboutDialog = false }, dismissText = "إغلاق") {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.brand_icon), null, Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("داونلودر", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text("الإصدار ${BuildConfig.VERSION_NAME}  ·  yt-dlp ${version ?: ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(14.dp))
                listOf(
                    "بلا إعلانات، بلا تتبّع، بلا تحليلات.",
                    "كل شيء يعمل على جهازك: لا خوادم وسيطة ولا واجهات خارجية.",
                    "يعتمد على yt-dlp و FFmpeg مفتوحَي المصدر، وخط «تجوّل».",
                    "حمّل فقط ما تملك حق تحميله، واحترم حقوق أصحاب المحتوى.",
                ).forEach {
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Box(Modifier.padding(top = 9.dp).size(4.dp).background(AppTheme.extra.gold, CircleShape))
                        Spacer(Modifier.width(10.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.padding(top = 10.dp).fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) { Column { content() } }
}

@Composable
private fun Divider() {
    Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

@Composable
private fun SettingRow(title: String, subtitle: String, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 68.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

@Composable
private fun Pill(text: String, danger: Boolean = false, loading: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
            .clickable(enabled = !loading, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(14.dp), color = AppTheme.extra.gold, strokeWidth = 2.dp)
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun Segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp)).padding(3.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier.heightIn(min = 38.dp).clip(RoundedCornerShape(9.dp))
                    .background(if (on) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
                    .clickable { onSelect(value) }.padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = if (on) AppTheme.extra.gold else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Stepper(value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        @Composable
        fun Btn(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, enabled: Boolean, click: () -> Unit) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape).clickable(enabled = enabled, onClick = click),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, desc, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.3f), modifier = Modifier.size(18.dp)) }
        }
        Btn(Icons.Rounded.Remove, "أقل", value > min) { onChange(value - 1) }
        Text("$value", style = MaterialTheme.typography.titleMedium.merge(NumberStyle), color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Btn(Icons.Rounded.Add, "أكثر", value < max) { onChange(value + 1) }
    }
}
