package com.downloader.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("settings")

data class AppSettings(
    /** 0 = الأعلى المتاح، -1 = صوت فقط، وإلا ارتفاع الجودة (1080، 720...). */
    val defaultQuality: Int = 0,
    val audioFormat: String = "mp3",
    val maxParallel: Int = 3,
    val lastEngineUpdate: Long = 0,
    val lastClipboardUrl: String = "",
    /** مجلد حفظ مخصص اختاره المستخدم (رابط شجرة SAF)، أو فارغ للمكان الافتراضي. */
    val saveTreeUri: String = "",
)

class SettingsStore(private val context: Context) {
    private object K {
        val quality = intPreferencesKey("default_quality")
        val audio = stringPreferencesKey("audio_format")
        val parallel = intPreferencesKey("max_parallel")
        val engineUpdate = longPreferencesKey("last_engine_update")
        val clip = stringPreferencesKey("last_clipboard_url")
        val saveTree = stringPreferencesKey("save_tree_uri")
    }

    val flow: Flow<AppSettings> = context.store.data.map { p ->
        AppSettings(
            defaultQuality = p[K.quality] ?: 0,
            audioFormat = p[K.audio] ?: "mp3",
            maxParallel = (p[K.parallel] ?: 3).coerceIn(1, 5),
            lastEngineUpdate = p[K.engineUpdate] ?: 0,
            lastClipboardUrl = p[K.clip] ?: "",
            saveTreeUri = p[K.saveTree] ?: "",
        )
    }

    suspend fun setDefaultQuality(v: Int) = context.store.edit { it[K.quality] = v }
    suspend fun setAudioFormat(v: String) = context.store.edit { it[K.audio] = v }
    suspend fun setMaxParallel(v: Int) = context.store.edit { it[K.parallel] = v.coerceIn(1, 5) }
    suspend fun setEngineUpdated(t: Long) = context.store.edit { it[K.engineUpdate] = t }
    suspend fun setSaveTreeUri(v: String) = context.store.edit { it[K.saveTree] = v }
    suspend fun setLastClipboardUrl(v: String) = context.store.edit { it[K.clip] = v }
}

val qualityChoices = listOf(
    0 to "الأعلى المتاح",
    2160 to "4K",
    1440 to "1440p",
    1080 to "1080p",
    720 to "720p",
    480 to "480p",
    360 to "360p",
    -1 to "صوت فقط",
)
