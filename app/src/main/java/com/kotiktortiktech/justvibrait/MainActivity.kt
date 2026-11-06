package com.kotiktortiktech.justvibrait

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kotiktortiktech.justvibrait.ui.JustVibraitApp
import com.kotiktortiktech.justvibrait.ui.theme.JustVibraitTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            val settings = viewModel.settings.collectAsStateWithLifecycle().value
            JustVibraitTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor
            ) {
                JustVibraitApp(viewModel)
            }
        }
    }
}
