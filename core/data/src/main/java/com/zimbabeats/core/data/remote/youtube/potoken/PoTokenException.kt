package com.zimbabeats.core.data.remote.youtube.potoken

class PoTokenException(message: String) : Exception(message)

/** Thrown if the WebView provided by the system is broken (e.g. too old to run the BotGuard JS). */
class BadWebViewException(message: String) : Exception(message)

fun buildExceptionForJsError(error: String): Exception {
    return if (error.contains("SyntaxError")) {
        BadWebViewException(error)
    } else {
        PoTokenException(error)
    }
}
