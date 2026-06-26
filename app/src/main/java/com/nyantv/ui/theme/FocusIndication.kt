package com.nyantv.ui.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

data class FocusIndication(
    private val borderColor: Color,
    private val cornerRadiusDp: Dp = 10.dp
) : IndicationNodeFactory {

    private inner class FocusNode(
        private val interactionSource: InteractionSource
    ) : Modifier.Node(), DrawModifierNode {

        var isFocused = false

        override fun onAttach() {
            coroutineScope.launch {
                interactionSource.interactions.collect { interaction ->
                    when (interaction) {
                        is FocusInteraction.Focus   -> { isFocused = true;  invalidateDraw() }
                        is FocusInteraction.Unfocus -> { isFocused = false; invalidateDraw() }
                    }
                }
            }
        }

        override fun ContentDrawScope.draw() {
            drawContent()
            if (isFocused) {
                val stroke    = 3f * density
                val haloWidth = stroke + 2f * density
                val inset     = haloWidth / 2f
                val cornerPx  = cornerRadiusDp.toPx()
                val topLeft   = Offset(inset, inset)
                val rectSize  = Size(size.width - inset * 2, size.height - inset * 2)
                // Dark edge under the ring so focus reads on same-coloured backgrounds.
                drawRoundRect(
                    color        = Color.Black.copy(alpha = 0.55f),
                    topLeft      = topLeft,
                    size         = rectSize,
                    style        = Stroke(width = haloWidth),
                    cornerRadius = CornerRadius(cornerPx)
                )
                drawRoundRect(
                    color        = borderColor,
                    topLeft      = topLeft,
                    size         = rectSize,
                    style        = Stroke(width = stroke),
                    cornerRadius = CornerRadius(cornerPx)
                )
            }
        }
    }

    override fun create(interactionSource: InteractionSource): Modifier.Node =
        FocusNode(interactionSource)
}