package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.creator.FontLibrary
import com.example.presentation.navigation.StickerApp
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FontLibrary.initialize(applicationContext)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                StickerApp()
            }
        }
    }
}
