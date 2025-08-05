package demo.trasnformer.transformer

import android.util.Size
import androidx.annotation.ColorInt
import kotlinx.serialization.Serializable

@Serializable
sealed interface Layer {
    val offset: Offset
    val size: LayerSize
}

@Serializable
data class TextLayer(
    override val offset: Offset,
    override val size: LayerSize,
    val text: String,
    @ColorInt val color: Int,
    val lineHeight: Int,
    val fontSize: Int = (lineHeight / 1.3).toInt(),
) : Layer

@Serializable
data class ShapeLayer(
    override val offset: Offset,
    override val size: LayerSize,

    @ColorInt val colorList: List<Int>,
    val clipShape: ClipShape,
    val shadow: Shadow? = null,

    ) : Layer {
    companion object {
        fun solidColor(
            offset: Offset,
            size: LayerSize,
            @ColorInt color: Int,
            clipShape: ClipShape,
            shadow: Shadow? = null,
        ) = ShapeLayer(
            offset = offset,
            size = size,
            colorList = listOf(color),
            clipShape = clipShape,
            shadow = shadow,
        )
    }
}

@Serializable
data class Shadow(
    val relativeOffset: Offset,
    val clipShape: ClipShape,
    @ColorInt val color: Int,
    val blurRadius: Float,
)

@Serializable
data class Offset(val x: Int, val y: Int) {
    operator fun plus(offset: Offset): Offset = Offset(x + offset.x, y + offset.y)

    companion object {
        val Zero: Offset = Offset(0, 0)
    }
}

@Serializable
data class LayerSize(val width: Int, val height: Int) {

    constructor(size: Int) : this(size, size)

    companion object {

        val FullScreen = LayerSize(1080, 1920)
    }
}


@Serializable
sealed interface ClipShape {

    @Serializable
    data object Circle : ClipShape

    @Serializable
    data object Rectangle : ClipShape

    @Serializable
    data class RoundedRectangle(
        val topLeftRadius: Int,
        val topRightRadius: Int,
        val bottomRightRadius: Int,
        val bottomLeftRadius: Int,
    ) : ClipShape {

        constructor(radius: Int) : this(radius, radius, radius, radius)
    }
}
