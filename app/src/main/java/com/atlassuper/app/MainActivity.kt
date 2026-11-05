package com.atlassuper.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = fillMaxSize,
                    color = MaterialTheme.colorScheme.background
                ) {
                    LaunchPlanScreen()
                }
            }
        }
    }
}
