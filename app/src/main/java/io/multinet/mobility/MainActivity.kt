package io.multinet.mobility

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import io.multinet.mobility.ui.MultiNetApp
import io.multinet.mobility.ui.theme.MultiNetTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MultiNetTheme {
                MultiNetApp()
            }
        }
    }
}
