package me.davidgomesdev.ofingidor.ui.service

actual fun getHost(): String = js("window.location.hostname") as String

actual fun isMobileDevice(): Boolean {
    val userAgent = js("navigator.userAgent") as String
    return userAgent.contains(Regex("Mobile|Android|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini"))
}

@Suppress("unused")
actual fun openUrl(url: String) {
    js("window.open(url, '_blank')")
}

actual fun shareConversation(text: String) {
    if (js("'share' in navigator") as Boolean) {
        js("navigator.share({ title: 'O Fingidor', text: text }).catch(function(e) { if (e.name !== 'AbortError') navigator.clipboard.writeText(text); })")
    } else {
        js("navigator.clipboard.writeText(text)")
    }
}

