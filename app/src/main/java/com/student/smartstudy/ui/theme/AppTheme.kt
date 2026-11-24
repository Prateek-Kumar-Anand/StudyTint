package com.student.smartstudy

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// White and gold palette. Accent is a deep gold that stays readable as text on white and under white button
// labels;
// Gold / GoldLight are the bright decorative golds (bars, gradients, headers).
val Accent = Color(0xFF9A6B00)

val Gold = Color(0xFFD4A017)

val GoldLight = Color(0xFFF6D874)

val GoldSoft = Color(0xFFFFF9E6)

val GoldRow = Color(0xFFFDFBF4)

val GoldLine = Color(0xFFEEE5CC)

val HeroInk = Color(0xFF2A1D00)

val GoldHeroColors = listOf(Color(0xFFF8DE7E), Color(0xFFE9B63A), Color(0xFFCF9519))

val CodePanel = Color(0xFF24252B)

val CodeInk = Color(0xFFF1EBD8)

val Bg = Color(0xFFFAFAF7)

val Ink = Color(0xFF24211A)

val Good = Color(0xFFD7F5E0)

val Bad = Color(0xFFFBDADA)

/**
 * The Material colour scheme: gold primary on white and cream, so default buttons, text buttons, dialogs and
 * fields all follow the theme.
 */
val GoldColorScheme = lightColorScheme(
    primary = Accent, onPrimary = Color.White, primaryContainer = GoldSoft, onPrimaryContainer = HeroInk,
    secondary = Gold, onSecondary = HeroInk, secondaryContainer = GoldSoft, onSecondaryContainer = HeroInk,
    tertiary = Color(0xFFB5651D), background = Bg, onBackground = Ink, surface = Color.White, onSurface = Ink,
    surfaceVariant = GoldSoft, onSurfaceVariant = Color(0xFF6B5A2A), outline = Color(0xFFD9BE72),
        outlineVariant = GoldLine,
    surfaceTint = Gold
)
