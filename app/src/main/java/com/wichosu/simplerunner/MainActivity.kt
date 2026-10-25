package com.wichosu.simplerunner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.wichosu.simplerunner.ui.navigation.AppNavHost
import com.wichosu.simplerunner.ui.theme.SimpleRunnerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SimpleRunnerTheme {
                AppNavHost()
            }
        }
    }
}
