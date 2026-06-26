package me.davidgomesdev.ofingidor.ui.service

@Suppress("USELESS_IS_CHECK")
@OptIn(ExperimentalWasmJsInterop::class)
actual fun isNetworkException(t: Throwable): Boolean = t is Exception || t is JsException
