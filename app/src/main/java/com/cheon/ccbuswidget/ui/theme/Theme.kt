package com.cheon.ccbuswidget.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontFamily
import com.cheon.ccbuswidget.R

@Composable
fun CcBusTheme(content: @Composable () -> Unit) {
    // 강조색은 res/values/colors.xml 의 app_accent
    val accent = colorResource(R.color.app_accent)
    val colorScheme = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = accent)
    } else {
        lightColorScheme(primary = accent)
    }

    // 삼성 기기에서는 시스템 글꼴(One UI Sans)을 쓴다. 없으면 기본 글꼴.
    val fontFamily = OneUiFont.fontFamily
    val typography = remember(fontFamily) { typographyWith(fontFamily) }

    MaterialTheme(colorScheme = colorScheme, typography = typography) {
        // 스타일을 따로 주지 않은 Text 도 같은 글꼴을 쓰도록 내려 준다
        CompositionLocalProvider(
            LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = fontFamily),
            content = content
        )
    }
}

/** Material 기본 타이포그래피에 글꼴만 바꿔 끼운다 */
private fun typographyWith(family: FontFamily): Typography {
    val d = Typography()
    return Typography(
        displayLarge = d.displayLarge.copy(fontFamily = family),
        displayMedium = d.displayMedium.copy(fontFamily = family),
        displaySmall = d.displaySmall.copy(fontFamily = family),
        headlineLarge = d.headlineLarge.copy(fontFamily = family),
        headlineMedium = d.headlineMedium.copy(fontFamily = family),
        headlineSmall = d.headlineSmall.copy(fontFamily = family),
        titleLarge = d.titleLarge.copy(fontFamily = family),
        titleMedium = d.titleMedium.copy(fontFamily = family),
        titleSmall = d.titleSmall.copy(fontFamily = family),
        bodyLarge = d.bodyLarge.copy(fontFamily = family),
        bodyMedium = d.bodyMedium.copy(fontFamily = family),
        bodySmall = d.bodySmall.copy(fontFamily = family),
        labelLarge = d.labelLarge.copy(fontFamily = family),
        labelMedium = d.labelMedium.copy(fontFamily = family),
        labelSmall = d.labelSmall.copy(fontFamily = family)
    )
}
