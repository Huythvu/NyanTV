package com.nyantv.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import org.json.JSONObject

// ─── Built-in themes ───────────────────────────────────────────────────────────

enum class AppTheme(val label: String) {
    SAKURA   ("Sakura"),
    OCEAN    ("Ocean"),
    MIDNIGHT ("Midnight"),
    FOREST   ("Forest"),
    SUNSET   ("Sunset"),
    OLED     ("OLED"),
    BLOOD("Blood")
}

// ─── Custom theme ──────────────────────────────────────────────

data class CustomTheme(
    val name:               String,
    val primary:            Color,
    val onPrimary:          Color,
    val primaryContainer:   Color,
    val onPrimaryContainer: Color,
    val secondary:          Color,
    val background:         Color,
    val surface:            Color,
    val surfaceContainer:   Color,
    val onBackground:       Color,
    val onSurface:          Color,
) {
    fun toColorScheme(): ColorScheme = darkColorScheme(
        primary             = primary,
        onPrimary           = onPrimary,
        primaryContainer    = primaryContainer,
        onPrimaryContainer  = onPrimaryContainer,
        secondary           = secondary,
        background          = background,
        surface             = surface,
        surfaceContainer    = surfaceContainer,
        onBackground        = onBackground,
        onSurface           = onSurface,
    )

    fun toJson(): String = JSONObject().apply {
        put("name",               name)
        put("primary",            primary.toHex())
        put("onPrimary",          onPrimary.toHex())
        put("primaryContainer",   primaryContainer.toHex())
        put("onPrimaryContainer", onPrimaryContainer.toHex())
        put("secondary",          secondary.toHex())
        put("background",         background.toHex())
        put("surface",            surface.toHex())
        put("surfaceContainer",   surfaceContainer.toHex())
        put("onBackground",       onBackground.toHex())
        put("onSurface",          onSurface.toHex())
    }.toString()

    companion object {
        fun fromJson(json: String): CustomTheme {
            val o = JSONObject(json)
            fun key(k: String) = o.getString(k).toColor()
            return CustomTheme(
                name               = o.getString("name"),
                primary            = key("primary"),
                onPrimary          = key("onPrimary"),
                primaryContainer   = key("primaryContainer"),
                onPrimaryContainer = key("onPrimaryContainer"),
                secondary          = key("secondary"),
                background         = key("background"),
                surface            = key("surface"),
                surfaceContainer   = key("surfaceContainer"),
                onBackground       = key("onBackground"),
                onSurface          = key("onSurface"),
            )
        }
    }
}

// ─── Helpers ───────────────────────────────────────────────────────────────────

private fun Color.toHex(): String =
    "#%08X".format((alpha * 255).toInt() shl 24 or
            ((red   * 255).toInt() shl 16) or
            ((green * 255).toInt() shl 8)  or
            (blue  * 255).toInt())

private fun String.toColor(): Color {
    val hex = trimStart('#')
    return when (hex.length) {
        6    -> Color(0xFF000000.toInt() or hex.toLong(16).toInt())
        8    -> Color(hex.toLong(16).toInt())
        else -> throw IllegalArgumentException("Invalid color: $this")
    }
}

// ─── Active theme holder ───────────────────────────────────────────────────────

sealed interface ActiveTheme {
    data class BuiltIn(val theme: AppTheme)     : ActiveTheme
    data class Custom(val theme: CustomTheme)   : ActiveTheme
}

fun ActiveTheme.colorScheme(): ColorScheme = when (this) {
    is ActiveTheme.BuiltIn -> this.theme.colorScheme()
    is ActiveTheme.Custom  -> this.theme.toColorScheme()
}

// ─── Color palettes ────────────────────────────────────────────────────────────

private val SakuraDark = darkColorScheme(
    primary          = Color(0xFFE8A0BF),
    onPrimary        = Color(0xFF4A1A2C),
    primaryContainer = Color(0xFF6B2D45),
    onPrimaryContainer= Color(0xFFFFD7E9),
    secondary        = Color(0xFFCCB8BE),
    background       = Color(0xFF1A1015),
    surface          = Color(0xFF221720),
    surfaceContainer = Color(0xFF2E2028),
    onBackground     = Color(0xFFEDE0E4),
    onSurface        = Color(0xFFEDE0E4),
)

private val OceanDark = darkColorScheme(
    primary          = Color(0xFF7FD1EE),
    onPrimary        = Color(0xFF00374D),
    primaryContainer = Color(0xFF004F6B),
    onPrimaryContainer= Color(0xFFC4E8F8),
    secondary        = Color(0xFFB2CAD6),
    background       = Color(0xFF0D1B21),
    surface          = Color(0xFF162530),
    surfaceContainer = Color(0xFF1E303C),
    onBackground     = Color(0xFFDEEEF5),
    onSurface        = Color(0xFFDEEEF5),
)

private val MidnightDark = darkColorScheme(
    primary          = Color(0xFFBBB3FF),
    onPrimary        = Color(0xFF251E6A),
    primaryContainer = Color(0xFF3C3482),
    onPrimaryContainer= Color(0xFFE3DFFF),
    secondary        = Color(0xFFC5C0D8),
    background       = Color(0xFF0E0E18),
    surface          = Color(0xFF16161F),
    surfaceContainer = Color(0xFF21212D),
    onBackground     = Color(0xFFE5E2F5),
    onSurface        = Color(0xFFE5E2F5),
)

private val ForestDark = darkColorScheme(
    primary          = Color(0xFF8FD4A1),
    onPrimary        = Color(0xFF00391D),
    primaryContainer = Color(0xFF1A5232),
    onPrimaryContainer= Color(0xFFACEEBF),
    secondary        = Color(0xFFB0CAB9),
    background       = Color(0xFF0C1510),
    surface          = Color(0xFF141F17),
    surfaceContainer = Color(0xFF1F2D22),
    onBackground     = Color(0xFFDEEDE4),
    onSurface        = Color(0xFFDEEDE4),
)

private val SunsetDark = darkColorScheme(
    primary          = Color(0xFFFFB075),
    onPrimary        = Color(0xFF4A2000),
    primaryContainer = Color(0xFF6B3200),
    onPrimaryContainer= Color(0xFFFFDCC2),
    secondary        = Color(0xFFD5BFA9),
    background       = Color(0xFF1C1208),
    surface          = Color(0xFF261A0D),
    surfaceContainer = Color(0xFF322316),
    onBackground     = Color(0xFFF2E0D0),
    onSurface        = Color(0xFFF2E0D0),
)

private val OledDark = darkColorScheme(
    primary             = Color(0xFFCFBCFF),
    onPrimary           = Color(0xFF381E72),
    primaryContainer    = Color(0xFF4F378B),
    onPrimaryContainer  = Color(0xFFEADDFF),
    secondary           = Color(0xFFCBC2DB),
    background          = Color(0xFF000000),
    surface             = Color(0xFF000000),
    surfaceContainer    = Color(0xFF111111),
    onBackground        = Color(0xFFEAE1F5),
    onSurface           = Color(0xFFEAE1F5),
)

private val BloodDark = darkColorScheme(
    primary            = Color(0xFFA90432),
    onPrimary          = Color(0xFFFFFFFF),
    primaryContainer   = Color(0xFF5A0016),
    onPrimaryContainer = Color(0xFFFFD9DF),
    secondary          = Color(0xFF673AB7),
    background         = Color(0xFF110004),
    surface            = Color(0xFF1C0007),
    surfaceContainer   = Color(0xFF2D0711),
    onBackground       = Color(0xFFFCEAEF),
    onSurface          = Color(0xFFFCEAEF),
)

fun AppTheme.colorScheme(): ColorScheme = when (this) {
    AppTheme.SAKURA   -> SakuraDark
    AppTheme.OCEAN    -> OceanDark
    AppTheme.MIDNIGHT -> MidnightDark
    AppTheme.FOREST   -> ForestDark
    AppTheme.SUNSET   -> SunsetDark
    AppTheme.OLED     -> OledDark
    AppTheme.BLOOD -> BloodDark
}

// ─── Root theme composable ─────────────────────────────────────────────────────

@Composable
fun NyanTVTheme(
    activeTheme: ActiveTheme = ActiveTheme.BuiltIn(AppTheme.SAKURA),
    content: @Composable () -> Unit
) {
    val colorScheme = activeTheme.colorScheme()
    MaterialTheme(colorScheme = colorScheme, typography = Typography()) {
        val indication = remember(colorScheme.primary) {
            FocusIndication(colorScheme.primary, cornerRadiusDp = 10.dp)
        }
        CompositionLocalProvider(LocalIndication provides indication) {
            content()
        }
    }
}