package com.inktrail.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.inktrail.reader.ui.navigation.InkTrailNavHost
import com.inktrail.reader.ui.theme.InkTrailTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            InkTrailTheme {
                InkTrailNavHost()
            }
        }
    }
}
