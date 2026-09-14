package com.github.toober77.fastpaste

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.github.toober77.fastpaste.ui.PasteListScreen
import com.github.toober77.fastpaste.ui.theme.FastPasteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FastPasteTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PasteListScreen()
                }
            }
        }
    }
}
