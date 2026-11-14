package com.m3e.clicker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.m3e.clicker.ui.ClickerScreen
import com.m3e.clicker.ui.M3EClickerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val bridge = ClickerBridge.get(applicationContext)
        setContent {
            M3EClickerTheme {
                ClickerScreen(bridge)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ClickerBridge.get(applicationContext).refresh()
    }
}
