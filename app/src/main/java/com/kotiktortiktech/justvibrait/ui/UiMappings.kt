package com.kotiktortiktech.justvibrait.ui

import androidx.annotation.StringRes
import com.kotiktortiktech.justvibrait.R
import com.kotiktortiktech.justvibrait.data.AppLanguage
import com.kotiktortiktech.justvibrait.data.ThemeMode
import com.kotiktortiktech.justvibrait.data.VibeProfile
import com.kotiktortiktech.justvibrait.media.LocalError
import com.kotiktortiktech.justvibrait.service.LiveError

@StringRes
fun LocalError.messageRes(): Int = when (this) {
    LocalError.OPEN -> R.string.error_file_open
    LocalError.PLAYBACK -> R.string.error_playback
}

@StringRes
fun LiveError.messageRes(): Int = when (this) {
    LiveError.PERMISSION -> R.string.error_permission
    LiveError.PROJECTION -> R.string.error_projection
    LiveError.AUDIO_INIT -> R.string.error_audio_init
    LiveError.CAPTURE -> R.string.error_capture
    LiveError.PROJECTION_STOPPED -> R.string.error_projection_stopped
}

@StringRes
fun VibeProfile.labelRes(): Int = when (this) {
    VibeProfile.SOFT -> R.string.profile_soft
    VibeProfile.SHARP -> R.string.profile_sharp
    VibeProfile.DYNAMIC -> R.string.profile_dynamic
}

@StringRes
fun VibeProfile.descriptionRes(): Int = when (this) {
    VibeProfile.SOFT -> R.string.profile_soft_desc
    VibeProfile.SHARP -> R.string.profile_sharp_desc
    VibeProfile.DYNAMIC -> R.string.profile_dynamic_desc
}

@StringRes
fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

@StringRes
fun AppLanguage.labelRes(): Int = when (this) {
    AppLanguage.SYSTEM -> R.string.language_system
    AppLanguage.RUSSIAN -> R.string.language_russian
    AppLanguage.ENGLISH -> R.string.language_english
}
