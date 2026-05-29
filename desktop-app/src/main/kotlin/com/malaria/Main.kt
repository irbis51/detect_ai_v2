package com.malaria

import androidx.compose.runtime.*
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.malaria.screens.MainScreen
import com.malaria.screens.AnalyzeScreen
import com.malaria.screens.HistoryScreen
import com.malaria.screens.AboutScreen
import com.malaria.database.DatabaseManager
import com.malaria.database.DatabaseBackup

fun main() = application {
    DatabaseManager.init()

    var currentScreen by remember { mutableStateOf("main") }

    Window(
        onCloseRequest = {
            DatabaseBackup.createBackup()
            exitApplication()
        },
        title = "AI Malaria Detection"
    ) {
        when (currentScreen) {
            "main" -> MainScreen(
                onAnalyzeClick = { currentScreen = "analyze" },
                onHistoryClick = { currentScreen = "history" },
                onAboutClick = { currentScreen = "about" }
            )
            "analyze" -> AnalyzeScreen(onBackClick = { currentScreen = "main" })
            "history" -> HistoryScreen(onBackClick = { currentScreen = "main" })
            "about" -> AboutScreen(onBackClick = { currentScreen = "main" })
        }
    }
}
