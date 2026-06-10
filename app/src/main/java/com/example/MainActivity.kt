package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.ui.NovaHome
import com.example.ui.NovaViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val viewModel: NovaViewModel by viewModels()

    companion object {
        @Volatile
        var isMainActivityActive: Boolean = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Start foreground service for background execution reality checks and execution
        com.example.NovaForegroundService.start(this)
        setContent {
            MyApplicationTheme {
                NovaHome(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        isMainActivityActive = true
    }

    override fun onStop() {
        isMainActivityActive = false
        super.onStop()
    }
}
