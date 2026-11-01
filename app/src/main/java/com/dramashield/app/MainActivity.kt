package com.dramashield.app

import android.app.ActivityManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dramashield.app.ui.navigation.DramaShieldApp
import com.dramashield.app.ui.theme.DramaShieldTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(R.string.window_title)
        updateTaskDescription()
        enableEdgeToEdge()
        setContent {
            DramaShieldTheme {
                DramaShieldApp()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun updateTaskDescription() {
        setTaskDescription(
            ActivityManager.TaskDescription(getString(R.string.window_title))
        )
    }
}
