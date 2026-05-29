package me.davidgomesdev.ofingidor.ui.service

expect fun getHost(): String
expect fun isMobileDevice(): Boolean
expect fun openUrl(url: String)
expect fun shareConversation(text: String)
