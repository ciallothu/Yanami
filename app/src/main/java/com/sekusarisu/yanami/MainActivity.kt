package com.sekusarisu.yanami

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import com.sekusarisu.yanami.data.local.preferences.UserPreferences
import com.sekusarisu.yanami.data.local.preferences.UserPreferencesRepository
import com.sekusarisu.yanami.ui.screen.server.ServerListScreen
import com.sekusarisu.yanami.ui.theme.ThemeColor
import com.sekusarisu.yanami.ui.theme.YanamiTheme
import org.koin.android.ext.android.inject

/**
 * 主入口 Activity
 *
 * 从 UserPreferencesRepository 读取主题偏好，传入 YanamiTheme。 使用 AppCompatActivity 以支持
 * AppCompatDelegate.setApplicationLocales 应用内语言切换。
 */
class MainActivity : AppCompatActivity() {

    private val prefsRepo: UserPreferencesRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val prefs by prefsRepo.preferencesFlow.collectAsState(initial = UserPreferences())

            val themeColor = ThemeColor.fromKey(prefs.themeColorKey)
            val darkTheme =
                    when (prefs.darkModeKey) {
                        "light" -> false
                        "dark" -> true
                        else -> isSystemInDarkTheme()
                    }

            YanamiTheme(themeColor = themeColor, darkTheme = darkTheme) {
                Navigator(ServerListScreen()) { navigator -> SlideTransition(navigator) }
            }
        }
    }
}
