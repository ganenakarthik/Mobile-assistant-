package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf

// Global mutable theme flags mapped dynamically based on selected preferences
var isLightThemeActive by mutableStateOf(false)
var isClassicDarkActive by mutableStateOf(false)

val SpaceBlack: Color
    get() = if (isLightThemeActive) Color(0xFFF6F8FA) else Color(0xFF05050A)

val CyberSlate: Color
    get() = if (isLightThemeActive) Color(0xFFFFFFFF) else Color(0xFF0B0C12)

val TechCard: Color
    get() = if (isLightThemeActive) Color(0x06000000) else Color(0x1C12131F)

val BorderSlate: Color
    get() = if (isLightThemeActive) Color(0x12000000) else Color(0x2800F0FF)

val CyberCyan: Color
    get() = if (isLightThemeActive) Color(0xFF007AFF) else if (isClassicDarkActive) Color(0xFFBB86FC) else Color(0xFF00F0FF)

val TechTeal: Color
    get() = if (isLightThemeActive) Color(0xFF5E5CE6) else if (isClassicDarkActive) Color(0xFF03DAC6) else Color(0xFF9F5AFD)

val SubCyan: Color
    get() = if (isLightThemeActive) Color(0x11007AFF) else Color(0x1800F0FF)

val CharcoalMuted: Color
    get() = if (isLightThemeActive) Color(0xFF6E6E73) else Color(0xFF94A3B8)

val NeonAmber: Color
    get() = if (isLightThemeActive) Color(0xFFFF9500) else Color(0xFFFFD700)

val GlowingRed: Color
    get() = if (isLightThemeActive) Color(0xFFFF3B30) else Color(0xFFFF2A6D)

val PureWhite: Color
    get() = if (isLightThemeActive) Color(0xFF1D1D1F) else Color(0xFFFAFAFA)

val TextSecondary: Color
    get() = if (isLightThemeActive) Color(0xFF48484A) else Color(0xFFE2E8F0)

val SageGreen: Color
    get() = if (isLightThemeActive) Color(0xFF34C759) else Color(0xFF22C55E)

