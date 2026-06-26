package me.davidgomesdev.ofingidor.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

fun isActionInputType(keyEvent: KeyEvent): Boolean =
    (keyEvent.isCtrlPressed || keyEvent.isMetaPressed) &&
        keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyDown
