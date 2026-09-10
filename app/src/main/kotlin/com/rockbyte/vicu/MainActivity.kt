package com.rockbyte.vicu

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rockbyte.vicu.nav.MainApp
import com.rockbyte.vicu.ui.theme.VicuTheme

private const val TAG = "Main"

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "OnCreated")
        enableEdgeToEdge()
        setContent {
            VicuTheme {
                MainApp()
            }
        }
    }
}
