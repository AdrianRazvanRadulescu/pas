package dev.pas.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

private const val STAGGER_MILLIS = 40
// Past the first few blocks the delay would only feel like lag.
private const val MAX_STAGGERED_INDEX = 6

/** Cards sink a little under the finger, so a tap feels physical. Pass the same source to clickable. */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Blocks of a screen rise into place one after another when the screen opens. */
@Composable
fun Modifier.enterInOrder(index: Int): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = 300, delayMillis = minOf(index, MAX_STAGGERED_INDEX) * STAGGER_MILLIS))
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 24.dp.toPx()
    }
}

/** Seconds left until [endsAtMillis], ticking once a second; 0 once it has passed. */
@Composable
fun rememberSecondsUntil(endsAtMillis: Long): Long {
    val secondsLeft by produceState(initialValue = secondsUntil(endsAtMillis), endsAtMillis) {
        while (value > 0) {
            delay(1000)
            value = secondsUntil(endsAtMillis)
        }
    }
    return secondsLeft
}

/** "1:05" */
fun clockLabel(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)

private fun secondsUntil(epochMillis: Long): Long =
    ((epochMillis - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
