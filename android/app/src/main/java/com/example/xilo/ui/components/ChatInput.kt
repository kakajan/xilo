package com.example.xilo.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.xilo.theme.XiloBlue
import com.example.xilo.theme.XiloSpacing

@Composable
fun ChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "نوشتن پیام...",
    showAttach: Boolean = true,
    showEmoji: Boolean = true
) {
    val sendScale by animateFloatAsState(
        targetValue = if (value.isNotBlank()) 1f else 0.85f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "sendScale"
    )
    val textStyle = MaterialTheme.typography.bodyLarge.forInput()

    Surface(
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
                .padding(horizontal = XiloSpacing.horizontal, vertical = XiloSpacing.vertical),
            verticalAlignment = Alignment.Bottom
        ) {
            if (showEmoji) {
                IconButton(
                    onClick = {},
                    modifier = Modifier.size(XiloSpacing.iconAction)
                ) {
                    XiloIcon(
                        icon = XiloIcons.Emoji,
                        contentDescription = "ایموجی",
                        modifier = Modifier.size(XiloSpacing.iconInline),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = {
                    androidx.compose.material3.Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .animateContentSize(),
                shape = RoundedCornerShape(24.dp),
                minLines = 1,
                maxLines = 6,
                textStyle = textStyle,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = XiloBlue,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                trailingIcon = if (showAttach) {
                    {
                        IconButton(onClick = {}) {
                            XiloIcon(
                                icon = XiloIcons.Attach,
                                contentDescription = "پیوست",
                                modifier = Modifier.size(XiloSpacing.iconInline),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                } else null
            )

            Spacer(modifier = Modifier.width(8.dp))

            AnimatedVisibility(
                visible = value.isNotBlank(),
                enter = scaleIn(spring(stiffness = Spring.StiffnessMedium)) + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                FloatingActionButton(
                    onClick = onSend,
                    shape = CircleShape,
                    containerColor = XiloBlue,
                    contentColor = Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                    modifier = Modifier
                        .size(44.dp)
                        .scale(sendScale)
                ) {
                    XiloSendIcon(
                        contentDescription = "ارسال",
                        modifier = Modifier.size(XiloSpacing.iconInline)
                    )
                }
            }
        }
    }
}
