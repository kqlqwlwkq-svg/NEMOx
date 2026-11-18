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

private val ZyvoraDarkColorScheme = darkColorScheme(
    primary = ZyvoraVioletPrimary,
    onPrimary = ZyvoraTextLight,
    primaryContainer = ZyvoraVioletContainer,
    onPrimaryContainer = ZyvoraLuminousViolet,
    secondary = ZyvoraLuminousViolet,
    onSecondary = ZyvoraVoidBg,
    secondaryContainer = ZyvoraLuminousContainer,
    onSecondaryContainer = ZyvoraTextLight,
    tertiary = ZyvoraAmber,
    onTertiary = ZyvoraVoidBg,
    tertiaryContainer = ZyvoraAmberContainer,
    onTertiaryContainer = ZyvoraAmber,
    background = ZyvoraVoidBg,
    onBackground = ZyvoraTextLight,
    surface = ZyvoraCardBg,
    onSurface = ZyvoraTextLight,
    surfaceVariant = ZyvoraCardElevated,
    onSurfaceVariant = ZyvoraTextMuted,
    surfaceContainerLowest = ZyvoraVoidBg,
    surfaceContainerLow = ZyvoraNavSurface,
    surfaceContainer = ZyvoraCardBg,
    surfaceContainerHigh = ZyvoraCardElevated,
    surfaceContainerHighest = ZyvoraCardHighest,
    outline = ZyvoraOutline,
    outlineVariant = ZyvoraOutline.copy(alpha = 0.55f),
    error = ZyvoraError,
    onError = ZyvoraVoidBg,
    errorContainer = ZyvoraErrorContainer,
    onErrorContainer = ZyvoraError
)

private val ZyvoraLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE2FF),
    onPrimaryContainer = Color(0xFF280B57),
    secondary = LightSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4D5FC),
    onSecondaryContainer = Color(0xFF25133D),
    tertiary = ZyvoraAmber,
    onTertiary = ZyvoraVoidBg,
    background = LightBg,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightSurfaceVariant,
    outline = LightOutline,
    error = ZyvoraError,
    onError = Color.White
)

val ZyvoraShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun ZyvoraTheme(
    darkTheme: Boolean = true,
    languageCode: String = "ar",
    content: @Composable () -> Unit
) {
    val baseContext = LocalContext.current
    val resolvedLocale = remember(languageCode) {
        when (languageCode) {
            "en" -> Locale("en")
            "tr" -> Locale("tr")
            "system" -> Locale.getDefault()
            else -> Locale("ar")
        }
    }

    val isArabic = resolvedLocale.language == "ar"

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

    val colorScheme = if (darkTheme) ZyvoraDarkColorScheme else ZyvoraLightColorScheme
    val typography = remember(isArabic) { zyvoraTypography(isArabic = isArabic) }

    CompositionLocalProvider(
        LocalContext provides wrappedContext,
        LocalConfiguration provides localizedConfig,
        LocalLayoutDirection provides layoutDirection
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = ZyvoraShapes,
            content = content
        )
    }
}

@Composable
fun NEMOxTheme(
    darkTheme: Boolean = true,
    languageCode: String = "ar",
    content: @Composable () -> Unit
) = ZyvoraTheme(darkTheme = darkTheme, languageCode = languageCode, content = content)
