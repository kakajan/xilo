package ir.xilo.app.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import ir.xilo.app.R

val YekanBakhFontFamily = FontFamily(
    Font(R.font.yekanbakh_regular, FontWeight.Normal),
    Font(R.font.yekanbakh_semibold, FontWeight.Medium),
    Font(R.font.yekanbakh_semibold, FontWeight.SemiBold),
    Font(R.font.yekanbakh_bold, FontWeight.Bold)
)

val IranSansXFontFamily = FontFamily(
    Font(R.font.iransansx_regular, FontWeight.Normal),
    Font(R.font.iransansx_medium, FontWeight.Medium),
    Font(R.font.iransansx_bold, FontWeight.Bold)
)

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = YekanBakhFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        textAlign = TextAlign.Center
    ),
    displayMedium = TextStyle(
        fontFamily = YekanBakhFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        textAlign = TextAlign.Center
    ),
    headlineLarge = TextStyle(
        fontFamily = YekanBakhFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        textAlign = TextAlign.Start
    ),
    headlineMedium = TextStyle(
        fontFamily = YekanBakhFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        textAlign = TextAlign.Start
    ),
    titleLarge = TextStyle(
        fontFamily = YekanBakhFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        textAlign = TextAlign.Start
    ),
    titleMedium = TextStyle(
        fontFamily = YekanBakhFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        textAlign = TextAlign.Start
    ),
    bodyLarge = TextStyle(
        fontFamily = IranSansXFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        textAlign = TextAlign.Start
    ),
    bodyMedium = TextStyle(
        fontFamily = IranSansXFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        textAlign = TextAlign.Start
    ),
    bodySmall = TextStyle(
        fontFamily = IranSansXFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        textAlign = TextAlign.Start
    ),
    labelLarge = TextStyle(
        fontFamily = YekanBakhFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = IranSansXFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp
    )
)
