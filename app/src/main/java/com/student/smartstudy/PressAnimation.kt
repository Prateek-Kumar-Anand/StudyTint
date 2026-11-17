package com.student.smartstudy

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer

/** Tap target with a soft spring press and a ripple clipped to the given shape. */
fun Modifier.tap(shape: Shape, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    // the animated value is read inside graphicsLayer, so a press redraws this one card instead of recomposing it every frame
    val scale = animateFloatAsState(
        if (pressed) 0.97f else 1f,
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "tap"
    )
    this.graphicsLayer { scaleX = scale.value; scaleY = scale.value }
        .clip(shape)
        .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
}
