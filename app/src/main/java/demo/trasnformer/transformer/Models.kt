package demo.trasnformer.transformer

import android.util.Size
import androidx.annotation.ColorInt
import kotlinx.serialization.Serializable

@Serializable
sealed interface Layer {
    val offset: LayerOffset
    val size: LayerSize
}

@Serializable
data class TextLayer(
    override val offset: LayerOffset,
    override val size: LayerSize,
    val text: String,
    @ColorInt val color: Int,
    val lineHeight: Int,
    val fontSize: Int = (lineHeight / 1.3).toInt(),
) : Layer

@Serializable
data class ShapeLayer(
    override val offset: LayerOffset,
    override val size: LayerSize,

    @ColorInt val colorList: List<Int>,
    @ColorInt val borderColor: Int? = null,
    val borderWidth: Int? = null,
) : Layer

@Serializable
data class LayerOffset(val x: Int, val y: Int) {
    operator fun plus(offset: LayerOffset): LayerOffset = LayerOffset(x + offset.x, y + offset.y)

    companion object {
        val Zero: LayerOffset = LayerOffset(0, 0)
    }
}

@Serializable
data class LayerSize(val width: Int, val height: Int) {

    constructor(size: Int) : this(size, size)

    companion object {

        val FullScreen: Size = Size(1080, 1920)
    }
}
