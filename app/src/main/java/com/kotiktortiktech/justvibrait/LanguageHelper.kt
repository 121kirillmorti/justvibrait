package com.kotiktortiktech.justvibrait

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.kotiktortiktech.justvibrait.data.AppLanguage

object LanguageHelper {

    fun apply(language: AppLanguage) {
        val locales = when (language) {
            AppLanguage.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
            AppLanguage.RUSSIAN -> LocaleListCompat.forLanguageTags("ru")
            AppLanguage.ENGLISH -> LocaleListCompat.forLanguageTags("en")
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
