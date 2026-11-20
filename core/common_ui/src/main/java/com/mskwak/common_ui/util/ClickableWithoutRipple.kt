package com.mskwak.common_ui.util

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

fun Modifier.clickableWithoutRipple(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    onClick: () -> Unit
): Modifier = composed {
    clickable(
        enabled = enabled,
        onClickLabel = onClickLabel,
        indication = null,
        interactionSource = remember { MutableInteractionSource() },
        onClick = onClick
    )
}