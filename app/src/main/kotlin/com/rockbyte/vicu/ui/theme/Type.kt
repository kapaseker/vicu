package com.rockbyte.vicu.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// ponytail: 暂用系统 sans-serif 近似 Inter（用户决策）；拿到 TTF 后改为
// FontFamily(Font(R.font.inter_regular), Font(R.font.inter_medium, FontWeight.Medium), ...)
// 并同步放入 res/font。字号/字重/行高/字距不变。
private val Inter: FontFamily = FontFamily.Default

/** DESIGN.md typography 刻度（Inter，标题负字距、正文宽行距）。 */
@Immutable
class VicuTypography(
    val h1: TextStyle,
    val h2: TextStyle,
    val h3: TextStyle,
    val bodyLg: TextStyle,
    val bodySm: TextStyle,
    val caption: TextStyle,
    val button: TextStyle,
)

val LightTypography = VicuTypography(
    h1 = TextStyle(
        fontFamily = Inter,
        fontSize = 64.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 70.sp,
        letterSpacing = (-0.02).em,
        color = LightAuralisColors.onSurface,
    ),
    h2 = TextStyle(
        fontFamily = Inter,
        fontSize = 40.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 48.sp,
        letterSpacing = (-0.01).em,
        color = LightAuralisColors.onSurface,
    ),
    h3 = TextStyle(
        fontFamily = Inter,
        fontSize = 24.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 31.sp,
        color = LightAuralisColors.onSurface,
    ),
    bodyLg = TextStyle(
        fontFamily = Inter,
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 26.sp,
        color = LightAuralisColors.onBackground,
    ),
    bodySm = TextStyle(
        fontFamily = Inter,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 22.sp,
        color = LightAuralisColors.onBackground,
    ),
    caption = TextStyle(
        fontFamily = Inter,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 17.sp,
        letterSpacing = 0.02.em,
        color = LightAuralisColors.onSurfaceVariant,
    ),
    // 不设色：按钮文案色由 VicuStyles 中按钮样式的 contentColor 继承
    button = TextStyle(
        fontFamily = Inter,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 14.sp,
    ),
)
