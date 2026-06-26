package me.davidgomesdev.ofingidor.ui

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import ofingidor.composeapp.generated.resources.Res
import ofingidor.composeapp.generated.resources.RobotoFlexVariable
import org.jetbrains.compose.resources.Font

@Composable
fun RobotoTypography(): Typography {
    val roboto =
        FontFamily(
            Font(Res.font.RobotoFlexVariable, FontWeight.Normal),
            Font(Res.font.RobotoFlexVariable, FontWeight.Medium),
            Font(Res.font.RobotoFlexVariable, FontWeight.Bold),
        )
    val defaults = Typography()
    return Typography(
        displayLarge = defaults.displayLarge.copy(fontFamily = roboto),
        displayMedium = defaults.displayMedium.copy(fontFamily = roboto),
        displaySmall = defaults.displaySmall.copy(fontFamily = roboto),
        headlineLarge = defaults.headlineLarge.copy(fontFamily = roboto),
        headlineMedium = defaults.headlineMedium.copy(fontFamily = roboto),
        headlineSmall = defaults.headlineSmall.copy(fontFamily = roboto),
        titleLarge = defaults.titleLarge.copy(fontFamily = roboto),
        titleMedium = defaults.titleMedium.copy(fontFamily = roboto),
        titleSmall = defaults.titleSmall.copy(fontFamily = roboto),
        bodyLarge = defaults.bodyLarge.copy(fontFamily = roboto),
        bodyMedium = defaults.bodyMedium.copy(fontFamily = roboto),
        bodySmall = defaults.bodySmall.copy(fontFamily = roboto),
        labelLarge = defaults.labelLarge.copy(fontFamily = roboto),
        labelMedium = defaults.labelMedium.copy(fontFamily = roboto),
        labelSmall = defaults.labelSmall.copy(fontFamily = roboto),
    )
}
