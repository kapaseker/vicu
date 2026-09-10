package com.rockbyte.vicu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rockbyte.vicu.nav.MainApp
import com.rockbyte.vicu.ui.theme.VicuTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VicuTheme {
                MainApp()
            }
        }
    }
}
