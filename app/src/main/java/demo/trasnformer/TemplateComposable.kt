package demo.trasnformer

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import demo.trasnformer.transformer.ClipShape
import demo.trasnformer.transformer.LayerCollection
import demo.trasnformer.transformer.ShapeLayer
import demo.trasnformer.transformer.TextLayer

@Composable
fun Template(layer: LayerCollection, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    Box(modifier = modifier) {
        layer.layers.forEach {
            when (it) {
                is ShapeLayer -> {
                    val colors = it.colorList.map(::Color)
                    Spacer(
                        modifier = Modifier
                            .then(it.clipShape.createModifier())
                            .width(with(density) { it.size.width.toDp() })
                            .height(with(density) { it.size.height.toDp() })
                            .then(
                                if (colors.size == 1) {
                                    Modifier.background(color = colors.first())
                                } else {
                                    Modifier.background(brush = Brush.verticalGradient(colors = colors))
                                }
                            )
                    )
                }

                is TextLayer -> {
                    val fontSize = with(LocalDensity.current) { it.fontSize.toSp() }
                    Text(
                        text = it.text,
                        color = Color(it.color),
                        fontSize = fontSize
                    )
                }
            }
        }
    }
}

@SuppressLint("ModifierFactoryExtensionFunction")
private fun ClipShape.createModifier(): Modifier = when (this) {
    is ClipShape.RoundedRectangle -> Modifier.clip(
        RoundedCornerShape(
            topStart = topLeftRadius.toFloat(),
            topEnd = topRightRadius.toFloat(),
            bottomStart = bottomLeftRadius.toFloat(),
            bottomEnd = bottomRightRadius.toFloat(),
        )
    )

    is ClipShape.Circle -> Modifier.clip(CircleShape)
    ClipShape.Rectangle -> Modifier
}
