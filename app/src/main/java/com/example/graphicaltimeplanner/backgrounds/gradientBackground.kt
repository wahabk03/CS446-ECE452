package com.example.graphicaltimeplanner.backgrounds

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.example.graphicaltimeplanner.ui.theme.Yellow
import com.example.graphicaltimeplanner.ui.theme.YellowVariant

// Gradient colors
fun Modifier.simpleGradient(): Modifier {
    return this.background(
        Brush.verticalGradient(
            colors = listOf(
                Yellow.copy(alpha = 0.80f),
                YellowVariant
            )
        )
    )
}