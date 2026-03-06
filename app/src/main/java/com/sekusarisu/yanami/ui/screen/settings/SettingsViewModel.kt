package com.sekusarisu.yanami.ui.screen.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import cafe.adriel.voyager.core.model.screenModelScope
import com.sekusarisu.yanami.data.local.preferences.UserPreferencesRepository
import com.sekusarisu.yanami.mvi.MviViewModel
import com.sekusarisu.yanami.mvi.UiEffect
import com.sekusarisu.yanami.mvi.UiEvent
import com.sekusarisu.yanami.mvi.UiState
import com.sekusarisu.yanami.ui.theme.ThemeColor
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** 设置页面 State */
data class SettingsState(
        val themeColor: ThemeColor = ThemeColor.DYNAMIC,
        val darkMode: String = "system",
        val language: String = "system"
) : UiState

/** 设置页面 Events */
sealed interface SettingsEvent : UiEvent {
    data class SetThemeColor(val color: ThemeColor) : SettingsEvent
    data class SetDarkMode(val mode: String) : SettingsEvent
    data class SetLanguage(val lang: String) : SettingsEvent
}

/** 设置页面 Effects */
sealed interface SettingsEffect : UiEffect {
    data class ShowToast(val message: String) : SettingsEffect
}

/** 设置 ViewModel */
class SettingsViewModel(private val prefsRepo: UserPreferencesRepository) :
        MviViewModel<SettingsState, SettingsEvent, SettingsEffect>(SettingsState()) {

    init {
        prefsRepo
                .preferencesFlow
                .onEach { prefs ->
                    setState {
                        copy(
                                themeColor = ThemeColor.fromKey(prefs.themeColorKey),
                                darkMode = prefs.darkModeKey,
                                language = prefs.languageKey
                        )
                    }
                }
                .launchIn(screenModelScope)
    }

    override fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.SetThemeColor -> {
                screenModelScope.launch { prefsRepo.setThemeColor(event.color.key) }
            }
            is SettingsEvent.SetDarkMode -> {
                screenModelScope.launch { prefsRepo.setDarkMode(event.mode) }
            }
            is SettingsEvent.SetLanguage -> {
                screenModelScope.launch {
                    prefsRepo.setLanguage(event.lang)
                    applyLocale(event.lang)
                }
            }
        }
    }

    /** 通过 AppCompatDelegate 应用 locale 切换 */
    private fun applyLocale(langKey: String) {
        val localeList =
                if (langKey == "system") {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(langKey)
                }
        AppCompatDelegate.setApplicationLocales(localeList)
    }
}
