package ir.xilo.app.ui.components

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import java.lang.Character.DIRECTIONALITY_LEFT_TO_RIGHT
import java.lang.Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING
import java.lang.Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE
import java.lang.Character.DIRECTIONALITY_RIGHT_TO_LEFT
import java.lang.Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
import java.lang.Character.getDirectionality

fun layoutDirectionForContent(
    text: String,
    emptyDefault: LayoutDirection = LayoutDirection.Rtl,
): LayoutDirection {
    var hasRtl = false
    var hasLtr = false
    for (char in text) {
        if (char.isWhitespace()) continue
        when (getDirectionality(char)) {
            DIRECTIONALITY_RIGHT_TO_LEFT,
            DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> hasRtl = true

            DIRECTIONALITY_LEFT_TO_RIGHT,
            DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
            DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE -> hasLtr = true
        }
    }
    return when {
        hasRtl && hasLtr -> emptyDefault
        hasRtl -> LayoutDirection.Rtl
        hasLtr -> LayoutDirection.Ltr
        else -> emptyDefault
    }
}

fun TextStyle.forInput(): TextStyle = copy(textDirection = TextDirection.Content)
