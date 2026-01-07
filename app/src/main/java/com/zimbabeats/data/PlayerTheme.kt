package com.zimbabeats.data

import androidx.compose.ui.graphics.Color

/**
 * App-wide accent color options
 */
enum class AccentColor(
    val displayName: String,
    val primary: Color,
    val primaryVariant: Color,
    val onPrimary: Color
) {
    GREEN("Green", Color(0xFF1DB954), Color(0xFF1ED760), Color.White),
    BLUE("Blue", Color(0xFF3B82F6), Color(0xFF60A5FA), Color.White),
    PURPLE("Purple", Color(0xFF8B5CF6), Color(0xFFA78BFA), Color.White),
    ORANGE("Orange", Color(0xFFFF8C00), Color(0xFFFFAB40), Color.Black),
    PINK("Pink", Color(0xFFEC4899), Color(0xFFF472B6), Color.White),
    TEAL("Teal", Color(0xFF14B8A6), Color(0xFF2DD4BF), Color.White),
    RED("Red", Color(0xFFEF4444), Color(0xFFF87171), Color.White)
}

/**
 * Download network preference
 */
enum class DownloadNetworkPreference(val displayName: String) {
    WIFI_ONLY("Wi-Fi only"),
    WIFI_AND_MOBILE("Wi-Fi + Mobile data")
}
