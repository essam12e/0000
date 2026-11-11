package com.downloader.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.downloader.app.data.LibraryItem
import com.downloader.app.data.TaskState
import com.downloader.app.ui.components.AppDialog
import com.downloader.app.ui.home.HomeEvent
import com.downloader.app.ui.home.HomeScreen
import com.downloader.app.ui.home.HomeViewModel
import com.downloader.app.ui.library.LibraryScreen
import com.downloader.app.ui.library.LibraryViewModel
import com.downloader.app.ui.library.shareItem
import com.downloader.app.ui.login.LoginScreen
import com.downloader.app.ui.player.PlayerScreen
import com.downloader.app.ui.settings.SettingsScreen
import com.downloader.app.ui.settings.SettingsViewModel
import com.downloader.app.ui.theme.AppTheme
import com.downloader.app.ui.theme.NumberStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.merge

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("الرئيسية", Icons.Rounded.Home),
    LIBRARY("تحميلاتي", Icons.Rounded.VideoLibrary),
    SETTINGS("الإعدادات", Icons.Rounded.Settings),
}

private val BarHeight = 68.dp

@Composable
fun AppRoot(tabState: MutableState<Tab>, homeVm: HomeViewModel) {
    val libraryVm: LibraryViewModel = viewModel()
    val settingsVm: SettingsViewModel = viewModel()
    val context = LocalContext.current
    var tab by tabState
    var showLogin by remember { mutableStateOf(false) }
    var playingId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var deleteFromPlayer by remember { mutableStateOf<LibraryItem?>(null) }

    val items by libraryVm.allItems.collectAsStateWithLifecycle()
    var duplicate by remember { mutableStateOf<com.downloader.app.data.HistoryEntry?>(null) }
    val tasks by libraryVm.tasks.collectAsStateWithLifecycle()
    val saving by libraryVm.saving.collectAsStateWithLifecycle()
    val playing = items.firstOrNull { it.id == playingId }

    // صلاحية الإشعارات (أندرويد 13+) تُطلب عند أول تحميل فقط.
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    // صلاحية التخزين: أندرويد 9 وأقدم فقط، وعند الحفظ للمعرض فقط.
    var pendingSave by remember { mutableStateOf<(() -> Unit)?>(null) }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pendingSave?.invoke() else message = "لا يمكن الحفظ في الجهاز بدون صلاحية التخزين"
        pendingSave = null
    }

    fun withStorage(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        ) action()
        else {
            pendingSave = action
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    LaunchedEffect(Unit) {
        homeVm.events.collect { e ->
            if (e is HomeEvent.ConfirmDuplicate) duplicate = e.previous
            if (e is HomeEvent.Enqueued) {
                tab = Tab.LIBRARY
                message = if (e.count > 1) "بدأ تحميل ${com.downloader.app.data.Fmt.elements(e.count)}" else "بدأ التحميل"
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    LaunchedEffect(Unit) { merge(libraryVm.messages, settingsVm.messages).collect { message = it } }
    LaunchedEffect(message) {
        if (message != null) {
            delay(2800)
            message = null
        }
    }

    BackHandler(enabled = tab != Tab.HOME && !showLogin && playing == null) { tab = Tab.HOME }

    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomInset = BarHeight + 20.dp + navInset

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).drawBehind {
            // وهج ذهبي خافت أعلى الشاشة — يكسر السواد المسطّح دون أن ينافس المحتوى.
            drawRect(
                Brush.radialGradient(
                    listOf(Color(0x1FD9B36A), Color.Transparent),
                    center = Offset(size.width * 0.85f, -size.height * 0.05f), radius = size.width * 0.9f,
                )
            )
        }
    ) {
        AnimatedContent(
            tab,
            transitionSpec = { (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.985f)) togetherWith fadeOut(tween(120)) },
            label = "tab",
        ) { current ->
            when (current) {
                Tab.HOME -> HomeScreen(homeVm, bottomInset, onOpenLogin = { showLogin = true })
                Tab.LIBRARY -> LibraryScreen(
                    libraryVm, bottomInset,
                    onOpen = { playingId = it.id },
                    onSave = { item -> withStorage { libraryVm.saveToGallery(item) } },
                    onSaveMany = { list -> withStorage { libraryVm.saveMany(list) } },
                    onReopenUrl = { url -> tab = Tab.HOME; homeVm.onShared(url) },
                )
                Tab.SETTINGS -> SettingsScreen(settingsVm, bottomInset, onOpenLogin = { showLogin = true })
            }
        }

        BottomBar(
            tab, { tab = it },
            activeCount = tasks.count { it.state == TaskState.RUNNING || it.state == TaskState.QUEUED },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        AnimatedVisibility(playing != null, enter = fadeIn() + slideInVertically { it / 8 }, exit = fadeOut()) {
            // نحتفظ بآخر عنصر أثناء حركة الخروج.
            var last by remember { mutableStateOf(playing) }
            if (playing != null) last = playing
            last?.let { item ->
                PlayerScreen(
                    item, saving = item.id in saving,
                    onClose = { playingId = null },
                    onSave = { withStorage { libraryVm.saveToGallery(item) { ok -> if (ok) playingId = null } } },
                    onShare = { shareItem(context, item) },
                    onDelete = { deleteFromPlayer = item },
                )
            }
        }

        AnimatedVisibility(showLogin, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            LoginScreen(
                onClose = { showLogin = false },
                onLoggedIn = { showLogin = false; message = "تم تسجيل الدخول — أعد تحليل الرابط" },
            )
        }

        // رسالة عائمة قصيرة فوق شريط التنقل.
        AnimatedVisibility(
            message != null,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomInset + 4.dp, start = 24.dp, end = 24.dp),
            enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut(),
        ) {
            var last by remember { mutableStateOf("") }
            message?.let { last = it }
            Surface(
                shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHighest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), shadowElevation = 8.dp,
            ) {
                Text(last, Modifier.padding(horizontal = 18.dp, vertical = 11.dp), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
            }
        }
    }

    duplicate?.let { prev ->
        AppDialog(
            "حمّلت هذا من قبل", onDismiss = { duplicate = null }, confirmText = "تحميل مرة أخرى",
            onConfirm = { duplicate = null; homeVm.download(force = true) },
        ) {
            Text(
                "«${prev.title}»\nآخر تحميل: ${com.downloader.app.data.Fmt.date(prev.at)}" + if (prev.label.isNotBlank()) "\nالجودة: ${prev.label}" else "",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    deleteFromPlayer?.let { item ->
        AppDialog(
            "حذف الملف؟", onDismiss = { deleteFromPlayer = null }, confirmText = "حذف نهائي", danger = true,
            onConfirm = { playingId = null; libraryVm.delete(item); deleteFromPlayer = null },
        ) {
            Text("سيُحذف «${item.title}» من التطبيق نهائياً.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * شريط تنقل مخصّص: كبسولة عائمة بحدّ شعري، والمحدد يُعلَّم بخط ذهبي قصير ينزلق بين العناصر
 * (بدل «حبة» Material الافتراضية).
 */
@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit, activeCount: Int, modifier: Modifier = Modifier) {
    val extra = AppTheme.extra
    Box(
        modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background)))
            .navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 10.dp, top = 10.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.97f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shadowElevation = 12.dp,
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(BarHeight)) {
                val slot = maxWidth / Tab.entries.size
                val indicatorX by animateDpAsState(
                    slot * current.ordinal + (slot - 28.dp) / 2,
                    spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow), label = "indicator",
                )
                Box(Modifier.offset(x = indicatorX).width(28.dp).height(3.dp).background(extra.goldBrush, RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp)))
                Row(Modifier.fillMaxSize()) {
                    Tab.entries.forEach { t ->
                        val selected = t == current
                        val tint by animateColorAsState(if (selected) extra.gold else extra.dim, label = "tint")
                        val textColor by animateColorAsState(if (selected) MaterialTheme.colorScheme.onSurface else extra.dim, label = "text")
                        Column(
                            Modifier.weight(1f).fillMaxSize().clickable(
                                remember { MutableInteractionSource() }, indication = null, role = Role.Tab,
                            ) { onSelect(t) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                        ) {
                            Box {
                                Icon(t.icon, null, tint = tint, modifier = Modifier.size(24.dp))
                                if (t == Tab.LIBRARY && activeCount > 0) {
                                    Box(
                                        Modifier.align(Alignment.TopEnd).offset(x = 9.dp, y = (-5).dp).size(17.dp).background(extra.goldBrush, CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) { Text("$activeCount", style = MaterialTheme.typography.labelSmall.merge(NumberStyle), color = extra.onGold) }
                                }
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(t.label, style = MaterialTheme.typography.labelSmall, color = textColor)
                        }
                    }
                }
            }
        }
    }
}
