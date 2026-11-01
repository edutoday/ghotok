package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

// Custom Bangla + English Heading Font Family (Noto Serif Bengali - Serif Display/Heading)
val HeadingFontFamily = FontFamily(
    Font(R.font.noto_serif_bengali_w400, FontWeight.Normal),
    Font(R.font.noto_serif_bengali_w500, FontWeight.Medium),
    Font(R.font.noto_serif_bengali_w600, FontWeight.SemiBold),
    Font(R.font.noto_serif_bengali_w700, FontWeight.Bold),
    Font(R.font.noto_serif_bengali_w700, FontWeight.ExtraBold)
)

// Custom Bangla + English Body Font Family (Hind Siliguri - Sans-Serif Body)
val BodyFontFamily = FontFamily(
    Font(R.font.hind_siliguri_w400, FontWeight.Light),
    Font(R.font.hind_siliguri_w400, FontWeight.Normal),
    Font(R.font.hind_siliguri_w500, FontWeight.Medium),
    Font(R.font.hind_siliguri_w600, FontWeight.SemiBold),
    Font(R.font.hind_siliguri_w700, FontWeight.Bold),
    Font(R.font.hind_siliguri_w700, FontWeight.ExtraBold)
)

private val defaultTypography = Typography()

val Typography = Typography(
    displayLarge = defaultTypography.displayLarge.copy(
        fontFamily = HeadingFontFamily,
        fontWeight = FontWeight.Bold
    ),
    displayMedium = defaultTypography.displayMedium.copy(
        fontFamily = HeadingFontFamily,
        fontWeight = FontWeight.Bold
    ),
    displaySmall = defaultTypography.displaySmall.copy(
        fontFamily = HeadingFontFamily,
        fontWeight = FontWeight.Bold
    ),
    headlineLarge = defaultTypography.headlineLarge.copy(
        fontFamily = HeadingFontFamily,
        fontWeight = FontWeight.Bold
    ),
    headlineMedium = defaultTypography.headlineMedium.copy(
        fontFamily = HeadingFontFamily,
        fontWeight = FontWeight.SemiBold
    ),
    headlineSmall = defaultTypography.headlineSmall.copy(
        fontFamily = HeadingFontFamily,
        fontWeight = FontWeight.SemiBold
    ),
    titleLarge = defaultTypography.titleLarge.copy(
        fontFamily = HeadingFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = defaultTypography.titleMedium.copy(
        fontFamily = HeadingFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp
    ),
    titleSmall = defaultTypography.titleSmall.copy(
        fontFamily = HeadingFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = defaultTypography.bodyLarge.copy(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.15.sp
    ),
    bodyMedium = defaultTypography.bodyMedium.copy(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.5.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.15.sp
    ),
    bodySmall = defaultTypography.bodySmall.copy(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp
    ),
    labelLarge = defaultTypography.labelLarge.copy(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = defaultTypography.labelMedium.copy(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.2.sp
    ),
    labelSmall = defaultTypography.labelSmall.copy(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.25.sp
    )
)

