package ir.xilo.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import ir.xilo.app.theme.XiloBlue
import ir.xilo.app.theme.XiloSpacing

@Composable
fun XiloButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: XiloButtonStyle = XiloButtonStyle.Primary,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val containerColor = when (style) {
        XiloButtonStyle.Primary -> XiloBlue
        XiloButtonStyle.Secondary -> MaterialTheme.colorScheme.surfaceVariant
        XiloButtonStyle.Outline -> Color.Transparent
        XiloButtonStyle.Danger -> MaterialTheme.colorScheme.error
    }

    val contentColor = when (style) {
        XiloButtonStyle.Primary -> Color.White
        XiloButtonStyle.Secondary -> MaterialTheme.colorScheme.onSurfaceVariant
        XiloButtonStyle.Outline -> MaterialTheme.colorScheme.primary
        XiloButtonStyle.Danger -> Color.White
    }

    val border = if (style == XiloButtonStyle.Outline) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    } else null

    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        enabled = enabled,
        shape = RoundedCornerShape(22.dp), // Telegram-like rounded style
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = border,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            )
        }
    }
}

enum class XiloButtonStyle {
    Primary, Secondary, Outline, Danger
}

@Composable
fun xiloTextFieldColors(
    isError: Boolean = false,
    focusedBorderColor: Color = XiloBlue,
    unfocusedBorderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
    transparentBorder: Boolean = false,
): TextFieldColors {
    val errorColor = MaterialTheme.colorScheme.error
    val focusedBorder = when {
        isError -> errorColor
        transparentBorder -> Color.Transparent
        else -> focusedBorderColor
    }
    val unfocusedBorder = when {
        isError -> errorColor
        transparentBorder -> Color.Transparent
        else -> unfocusedBorderColor
    }
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = focusedBorder,
        unfocusedBorderColor = unfocusedBorder,
        disabledBorderColor = unfocusedBorder,
        errorBorderColor = errorColor,
        errorCursorColor = errorColor,
        errorSupportingTextColor = errorColor,
        cursorColor = if (isError) errorColor else focusedBorderColor,
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
fun XiloTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    isError: Boolean = false,
    errorText: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge.forInput(),
    colors: TextFieldColors? = null,
    transparentBorder: Boolean = false,
) {
    val fieldColors = colors ?: xiloTextFieldColors(
        isError = isError,
        transparentBorder = transparentBorder,
    )

    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    text = placeholder,
                    style = textStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            isError = isError,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            textStyle = textStyle,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = fieldColors,
            supportingText = if (isError && !errorText.isNullOrBlank()) {
                {
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.bodySmall.forInput(),
                    )
                }
            } else {
                null
            },
        )
    }
}

@Composable
fun XiloTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    minLines: Int = 3,
    maxLines: Int = Int.MAX_VALUE,
    isError: Boolean = false,
    errorText: String? = null,
    transparentBorder: Boolean = false,
) {
    XiloTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        modifier = modifier,
        singleLine = false,
        minLines = minLines,
        maxLines = maxLines,
        isError = isError,
        errorText = errorText,
        transparentBorder = transparentBorder,
    )
}

@Composable
fun XiloAvatar(
    imageUrl: String?,
    size: Dp = 48.dp,
    hasStory: Boolean = false,
    isOnline: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else Modifier

    Box(
        modifier = modifier
            .size(size)
            .then(clickableModifier),
        contentAlignment = Alignment.Center
    ) {
        val avatarSize = if (hasStory) size - 6.dp else size
        
        // Story Border Circle
        if (hasStory) {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.sweepGradient(
                            listOf(
                                Color(0xFF1D9BF0),
                                Color(0xFF00BA7C),
                                Color(0xFFFFAD00),
                                Color(0xFF1D9BF0)
                            )
                        )
                    )
            )
            // Gap between border and avatar
            Box(
                modifier = Modifier
                    .size(size - 3.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background)
            )
        }

        // Main Image
        AsyncImage(
            model = imageUrl ?: "https://www.gravatar.com/avatar/00000000000000000000000000000000?d=mp&f=y",
            contentDescription = "Avatar",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(avatarSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )

        // Online Status Dot
        if (isOnline) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .align(Alignment.BottomEnd)
                    .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                    .background(Color(0xFF00BA7C), CircleShape)
            )
        }
    }
}

@Composable
fun FloatingBottomNavigation(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    items: List<NavigationItem>,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(XiloSpacing.bottomNavBarHeight / 2),
        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
        ),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.7f),
                    Color.White.copy(alpha = 0.1f)
                )
            )
        ),
        modifier = modifier
            .padding(
                start = 24.dp,
                end = 24.dp,
                top = XiloSpacing.bottomNavOuterTopPadding,
                bottom = XiloSpacing.bottomNavOuterBottomPadding
            )
            .height(XiloSpacing.bottomNavBarHeight)
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = selectedTab == index
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.08f else 1f,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "iconScale"
                )
                val tint by animateColorAsState(
                    targetValue = if (isSelected) XiloBlue else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    label = "iconTint"
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isSelected) XiloBlue.copy(alpha = 0.12f) else Color.Transparent
                        )
                        .clickable(onClick = { onTabSelected(index) })
                        .padding(horizontal = if (isSelected) 14.dp else 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.scale(scale)
                    ) {
                        XiloIcon(
                            icon = if (isSelected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.label,
                            tint = tint,
                            modifier = Modifier.size(24.dp)
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = item.label,
                                color = XiloBlue,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

data class NavigationItem(
    val label: String,
    @androidx.annotation.DrawableRes val selectedIcon: Int,
    @androidx.annotation.DrawableRes val unselectedIcon: Int
)
