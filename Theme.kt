package iq.school.manager.ui.theme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

val Navy = Color(0xFF0F2547); val Navy2 = Color(0xFF1B3A6B); val Gold = Color(0xFFB8923A)
private val Light = lightColorScheme(primary = Navy, onPrimary = Color.White, secondary = Gold, background = Color(0xFFF4F5F8), surface = Color.White)
private val Dark = darkColorScheme(primary = Color(0xFF8FB0E8), secondary = Gold, background = Color(0xFF0B1220), surface = Color(0xFF131D33))

@Composable fun SchoolTheme(content: @Composable () -> Unit) {
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
  }
}
