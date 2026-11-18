package com.example.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.text.TextUtils
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.util.Locale

private val NemoxDarkColorScheme = darkColorScheme(
    primary = NeonGreen,
    onPrimary = ObsidianBg,
    primaryContainer = NeonGreenContainer,
    onPrimaryContainer = NeonGreen,
    secondary = ElectricBlue,
    onSecondary = ObsidianBg,
    secondaryContainer = ElectricBlueContainer,
    onSecondaryContainer = ElectricBlue,
    tertiary = CyberAmber,
    onTertiary = ObsidianBg,
    tertiaryContainer = CyberAmberContainer,
    onTertiaryContainer = CyberAmber,
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceElevated,
    surfaceContainerHigh = ObsidianSurfaceHighest,
    outline = ObsidianOutline,
    error = CyberRed,
    onError = Color.White,
    errorContainer = CyberRedContainer,
    onErrorContainer = CyberRed
)

private val NemoxLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD0F5E3),
    onPrimaryContainer = Color(0xFF00381C),
    secondary = LightSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1F1FF),
    onSecondaryContainer = Color(0xFF003547),
    tertiary = CyberAmber,
    onTertiary = ObsidianBg,
    background = LightBg,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = Color(0xFFCBD5E1),
    error = CyberRed,
    onError = Color.White
)

val NemoxShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun NEMOxTheme(
    darkTheme: Boolean = true,
    languageCode: String = "system",
    content: @Composable () -> Unit
) {
    val baseContext = LocalContext.current
    val resolvedLocale = remember(languageCode) {
        when (languageCode) {
            "ar" -> Locale("ar")
            "en" -> Locale("en")
            else -> Locale.getDefault()
        }
    }

    val localizedConfig = remember(baseContext, resolvedLocale) {
        Configuration(baseContext.resources.configuration).apply {
            setLocale(resolvedLocale)
            setLayoutDirection(resolvedLocale)
        }
    }

    val wrappedContext = remember(baseContext, localizedConfig) {
        val localizedResources: Resources = baseContext.createConfigurationContext(localizedConfig).resources
        object : ContextWrapper(baseContext) {
            override fun getResources(): Resources = localizedResources
        }
    }

    val layoutDirection = remember(resolvedLocale) {
        if (TextUtils.getLayoutDirectionFromLocale(resolvedLocale) == android.view.View.LAYOUT_DIRECTION_RTL) {
            LayoutDirection.Rtl
        } else {
            LayoutDirection.Ltr
        }
    }

    val colorScheme = if (darkTheme) NemoxDarkColorScheme else NemoxLightColorScheme

    CompositionLocalProvider(
        LocalContext provides wrappedContext,
        LocalConfiguration provides localizedConfig,
        LocalLayoutDirection provides layoutDirection
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = NemoxTypography,
            shapes = NemoxShapes,
            content = content
        )
    }
}
