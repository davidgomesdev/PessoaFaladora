package me.davidgomesdev.ofingidor.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application

fun main() =
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "O Fingidor",
            alwaysOnTop = false,
            state = WindowState(placement = WindowPlacement.Floating, width = 800.dp, height = 800.dp),
        ) {
            App()
        }
    }
