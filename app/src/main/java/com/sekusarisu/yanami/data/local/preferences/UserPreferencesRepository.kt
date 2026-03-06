package com.sekusarisu.yanami.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
/** DataStore 扩展属性 */
private val Context.dataStore: DataStore<Preferences> by
        preferencesDataStore(name = "user_preferences")

/**
 * 用户偏好仓库
 *
 * 使用 DataStore Preferences 持久化：
 * - 主题颜色 (dynamic / teal / blue / purple / pink / orange / green)
 * - 深色模式 (system / light / dark)
 * - 语言 (system / zh / en / ja)
 */
class UserPreferencesRepository(private val context: Context) {

    companion object {
        private val THEME_COLOR_KEY = stringPreferencesKey("theme_color")
        private val DARK_MODE_KEY = stringPreferencesKey("dark_mode")
        private val LANGUAGE_KEY = stringPreferencesKey("language")
        private val TERMINAL_FONT_SIZE_KEY = intPreferencesKey("terminal_font_size")
        const val DEFAULT_TERMINAL_FONT_SIZE = 20
    }

    /** 偏好数据流 */
    val preferencesFlow: Flow<UserPreferences> =
            context.dataStore.data.map { prefs ->
                UserPreferences(
                        themeColorKey = prefs[THEME_COLOR_KEY] ?: "dynamic",
                        darkModeKey = prefs[DARK_MODE_KEY] ?: "system",
                        languageKey = prefs[LANGUAGE_KEY] ?: "system"
                )
            }

    /** 设置主题颜色 */
    suspend fun setThemeColor(key: String) {
        context.dataStore.edit { it[THEME_COLOR_KEY] = key }
    }

    /** 设置深色模式 */
    suspend fun setDarkMode(key: String) {
        context.dataStore.edit { it[DARK_MODE_KEY] = key }
    }

    /** 设置语言 */
    suspend fun setLanguage(key: String) {
        context.dataStore.edit { it[LANGUAGE_KEY] = key }
    }

    /** 终端字号 Flow */
    val terminalFontSize: Flow<Int> =
            context.dataStore.data.map { prefs ->
                prefs[TERMINAL_FONT_SIZE_KEY] ?: DEFAULT_TERMINAL_FONT_SIZE
            }

    /** 保存终端字号 */
    suspend fun setTerminalFontSize(size: Int) {
        context.dataStore.edit { it[TERMINAL_FONT_SIZE_KEY] = size }
    }
}

/** 用户偏好数据类 */
data class UserPreferences(
        val themeColorKey: String = "dynamic",
        val darkModeKey: String = "system",
        val languageKey: String = "system"
)
