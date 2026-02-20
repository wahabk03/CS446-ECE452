package com.example.graphicaltimeplanner.backgrounds

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.graphicaltimeplanner.ui.theme.Yellow
import com.example.graphicaltimeplanner.ui.theme.YellowVariant
import com.example.graphicaltimeplanner.ui.theme.White

// Gradient colors
fun Modifier.simpleGradient(): Modifier {
    return this.background(
        Brush.verticalGradient(
            colors = listOf(
                White,
                Yellow,
                YellowVariant
            )
        )
    )
}

/*
// Alternative horizontal gradient
fun Modifier.simpleGradientHorizontal(): Modifier {
    return this.background(
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF4CAF50), // Green 500
                Color(0xFF2196F3)  // Blue 500
            )
        )
    )
}

// Customizable gradient function
fun Modifier.simpleGradient(
    startColor: Color = Color(0xFF2196F3),
    endColor: Color = Color(0xFFE91E63),
    isVertical: Boolean = true
): Modifier {
    val brush = if (isVertical) {
        Brush.verticalGradient(
            colors = listOf(startColor, endColor)
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(startColor, endColor)
        )
    }
    return this.background(brush)
}
 */