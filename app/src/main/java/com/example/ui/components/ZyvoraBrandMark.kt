package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.ZyvoraCardBg
import com.example.ui.theme.ZyvoraLuminousViolet
import com.example.ui.theme.ZyvoraTextLight
import com.example.ui.theme.ZyvoraVioletPrimary
import com.example.ui.theme.ZyvoraVoidBg

/**
 * Standalone geometric ZYVORA symbol:
 * Faceted portal doorway enclosing an angled geometric Z gateway beam.
 */
@Composable
fun ZyvoraGeometricMark(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    glowAlpha: Float = 0.28f
) {
    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "ZYVORA geometric portal mark" }
    ) {
        val w = this.size.width
        val h = this.size.height

        // Ambient portal glow
        if (glowAlpha > 0f) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ZyvoraLuminousViolet.copy(alpha = glowAlpha),
                        ZyvoraVioletPrimary.copy(alpha = glowAlpha * 0.45f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.5f, h * 0.5f),
                    radius = w * 0.62f
                ),
                radius = w * 0.62f,
                center = Offset(w * 0.5f, h * 0.5f)
            )
        }

        // Outer octagonal dimensional portal frame
        val portal = Path().apply {
            moveTo(w * 0.22f, h * 0.08f)
            lineTo(w * 0.78f, h * 0.08f)
            lineTo(w * 0.92f, h * 0.24f)
            lineTo(w * 0.92f, h * 0.76f)
            lineTo(w * 0.78f, h * 0.92f)
            lineTo(w * 0.22f, h * 0.92f)
            lineTo(w * 0.08f, h * 0.76f)
            lineTo(w * 0.08f, h * 0.24f)
            close()
        }

        drawPath(
            path = portal,
            brush = Brush.linearGradient(
                colors = listOf(ZyvoraCardBg, ZyvoraVoidBg),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
        )
        drawPath(
            path = portal,
            brush = Brush.linearGradient(
                colors = listOf(ZyvoraLuminousViolet, ZyvoraVioletPrimary),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            ),
            style = Stroke(width = w * 0.075f, join = StrokeJoin.Round)
        )

        // Inner portal threshold accent lines (top-left & bottom-right facets)
        val innerPortal = Path().apply {
            moveTo(w * 0.27f, h * 0.18f)
            lineTo(w * 0.73f, h * 0.18f)
            lineTo(w * 0.82f, h * 0.29f)
            lineTo(w * 0.82f, h * 0.71f)
            lineTo(w * 0.73f, h * 0.82f)
            lineTo(w * 0.27f, h * 0.82f)
            lineTo(w * 0.18f, h * 0.71f)
            lineTo(w * 0.18f, h * 0.29f)
            close()
        }
        drawPath(
            path = innerPortal,
            color = ZyvoraVioletPrimary.copy(alpha = 0.45f),
            style = Stroke(width = w * 0.025f, join = StrokeJoin.Round)
        )

        // Geometric Z gateway beam cutting through the portal
        val zBeam = Path().apply {
            moveTo(w * 0.29f, h * 0.30f)
            lineTo(w * 0.71f, h * 0.30f)
            lineTo(w * 0.29f, h * 0.70f)
            lineTo(w * 0.71f, h * 0.70f)
        }
        drawPath(
            path = zBeam,
            brush = Brush.linearGradient(
                colors = listOf(ZyvoraTextLight, ZyvoraLuminousViolet, ZyvoraVioletPrimary),
                start = Offset(w * 0.25f, h * 0.25f),
                end = Offset(w * 0.75f, h * 0.75f)
            ),
            style = Stroke(
                width = w * 0.11f,
                cap = StrokeCap.Square,
                join = StrokeJoin.Bevel
            )
        )
    }
}

/**
 * ZYVORA Brand Lockup: standalone geometric mark placed cleanly beside the ZYVORA wordmark.
 */
@Composable
fun ZyvoraBrandLockup(
    modifier: Modifier = Modifier,
    markSize: Dp = 32.dp,
    vertical: Boolean = false,
    tagline: String? = null
) {
    if (vertical) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ZyvoraGeometricMark(size = markSize, glowAlpha = 0.42f)
            Spacer(modifier = Modifier.height(14.dp))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = "ZYVORA",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    letterSpacing = 5.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            if (!tagline.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = tagline,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = modifier,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ZyvoraGeometricMark(size = markSize, glowAlpha = 0.2f)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "ZYVORA",
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        letterSpacing = 3.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (!tagline.isNullOrBlank()) {
                        Text(
                            text = tagline,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}
