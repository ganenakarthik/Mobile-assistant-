package com.example.ui.theme

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext

val currentThemeState = mutableStateOf("Cyber Dark")

private val NovaDarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    secondary = TechTeal,
    tertiary = SubCyan,
    background = SpaceBlack,
    surface = CyberSlate,
    onPrimary = SpaceBlack,
    onSecondary = SpaceBlack,
    onTertiary = SpaceBlack,
    onBackground = PureWhite,
    onSurface = PureWhite,
    surfaceVariant = TechCard,
    onSurfaceVariant = TextSecondary,
    outline = BorderSlate
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val sharedPref = remember { context.getSharedPreferences("nova_settings_prefs", Context.MODE_PRIVATE) }
    
    // Initialize the theme state from preferences once
    remember {
        val initial = sharedPref.getString("selected_theme", "Cyber Dark") ?: "Cyber Dark"
        currentThemeState.value = initial
        true
    }

    val selectedTheme = currentThemeState.value

    // Set global theme switcher flags based on user preference
    isLightThemeActive = (selectedTheme == "Light Mode")
    isClassicDarkActive = (selectedTheme == "Classic Dark")

    val scheme = if (isLightThemeActive) {
        lightColorScheme(
            primary = CyberCyan,
            secondary = TechTeal,
            tertiary = SubCyan,
            background = SpaceBlack,
            surface = CyberSlate,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = PureWhite,
            onSurface = PureWhite,
            surfaceVariant = TechCard,
            onSurfaceVariant = TextSecondary,
            outline = BorderSlate
        )
    } else {
        NovaDarkColorScheme
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = Typography,
        content = content
    )
}
