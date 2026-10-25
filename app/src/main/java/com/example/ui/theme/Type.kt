package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val JetBrainsMono = FontFamily(
  Font(R.font.jetbrains_mono, FontWeight.Normal),
  Font(R.font.jetbrains_mono, FontWeight.Bold),
)

val Inter = FontFamily(
  Font(R.font.inter, FontWeight.Normal),
  Font(R.font.inter, FontWeight.Medium),
  Font(R.font.inter, FontWeight.Bold),
)

val Typography = Typography(
  displayLarge = TextStyle(
    fontFamily = Inter,
    fontWeight = FontWeight.Bold,
    fontSize = 32.sp,
    lineHeight = 38.sp,
    letterSpacing = (-0.5).sp,
  ),
  titleLarge = TextStyle(
    fontFamily = Inter,
    fontWeight = FontWeight.SemiBold,
    fontSize = 20.sp,
    lineHeight = 26.sp,
  ),
  titleMedium = TextStyle(
    fontFamily = Inter,
    fontWeight = FontWeight.Medium,
    fontSize = 16.sp,
    lineHeight = 22.sp,
  ),
  bodyLarge = TextStyle(
    fontFamily = Inter,
    fontWeight = FontWeight.Normal,
    fontSize = 15.sp,
    lineHeight = 22.sp,
  ),
  bodyMedium = TextStyle(
    fontFamily = Inter,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 18.sp,
  ),
  labelLarge = TextStyle(
    fontFamily = JetBrainsMono,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 18.sp,
  ),
  labelMedium = TextStyle(
    fontFamily = JetBrainsMono,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
  ),
  labelSmall = TextStyle(
    fontFamily = JetBrainsMono,
    fontWeight = FontWeight.Normal,
    fontSize = 11.sp,
    lineHeight = 14.sp,
  ),
)
