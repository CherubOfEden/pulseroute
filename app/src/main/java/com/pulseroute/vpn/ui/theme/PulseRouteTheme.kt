package com.pulseroute.vpn.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val PulseGreen = Color(0xFF6EE7B7)
private val PulseBackground = Color(0xFF07110F)
private val PulseSurface = Color(0xFF101B18)
private val PulseSurfaceVariant = Color(0xFF1A2925)

private val PulseColors = darkColorScheme(
    primary = PulseGreen,
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF164E3E),
    onPrimaryContainer = Color(0xFFB7F7DE),
    secondary = Color(0xFF8FD5FF),
    onSecondary = Color(0xFF00344A),
    secondaryContainer = Color(0xFF114A63),
    onSecondaryContainer = Color(0xFFC7E9FF),
    tertiary = Color(0xFFFFD48A),
    onTertiary = Color(0xFF412D00),
    background = PulseBackground,
    onBackground = Color(0xFFE5F1EC),
    surface = PulseSurface,
    onSurface = Color(0xFFE5F1EC),
    surfaceVariant = PulseSurfaceVariant,
    onSurfaceVariant = Color(0xFFB8C9C2),
    outline = Color(0xFF45564F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val PulseShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun PulseRouteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PulseColors,
        shapes = PulseShapes,
        content = content,
    )
}
