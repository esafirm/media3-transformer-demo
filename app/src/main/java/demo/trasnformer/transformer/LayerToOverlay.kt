package demo.trasnformer.transformer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color.alpha
import android.graphics.Color.blue
import android.graphics.Color.green
import android.graphics.Color.red
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Size
import android.util.SizeF
import androidx.annotation.OptIn
import androidx.core.graphics.createBitmap
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextureOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.invoke
import kotlin.math.max
import kotlin.math.roundToInt

data class LayerConfiguration(val layers: List<Layer>)

@OptIn(UnstableApi::class)
internal suspend fun LayerConfiguration.toOverlays(
    frameSize: Size,
    originalFrameSize: Size,
): List<TextureOverlay> = Default {
    val scale = max(
        frameSize.width.toFloat() / originalFrameSize.width,
        frameSize.height.toFloat() / originalFrameSize.height
    )
    adjustedLayers(scale).mapParallel { layer ->
        when (layer) {
            is ShapeLayer -> layer.toOverlay(frameSize)
            is TextLayer -> layer.toOverlay(frameSize)
        }
    }
}

internal suspend fun LayerConfiguration.toBitmaps(
    frameSize: Size,
    originalFrameSize: Size,
) {

}

/**
 * Adjusts the size and offset of each layer in the configuration based on the provided scale.
 *
 * @param scale The scale factor to adjust the layers.
 * @return A list of adjusted layers.
 */
private fun LayerConfiguration.adjustedLayers(scale: Float) = layers.map {
    when (it) {
        is ShapeLayer -> it.copy(
            size = it.size * scale,
            offset = it.offset * scale,
        )

        is TextLayer -> {
            it.copy(
                size = it.size * scale,
                offset = it.offset * scale,
                lineHeight = (it.lineHeight * scale).roundToInt(),
                fontSize = (it.fontSize * scale).roundToInt(),
            )
        }
    }
}

@OptIn(UnstableApi::class)
private fun ShapeLayer.toOverlay(frameSize: Size): TextureOverlay {
    val layer = this
    val shapeWidth = layer.size.width.toFloat()
    val shapeHeight = layer.size.height.toFloat()

    val bitmapSize = SizeF(shapeWidth, shapeHeight)

    val bitmap = createBitmap(
        bitmapSize.width.roundToInt(),
        bitmapSize.height.roundToInt(),
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    val paint = Paint().apply {
        isAntiAlias = true
        val colorList = layer.colorList
        if (colorList.isNotEmpty()) {
            val newColorList = colorList.map { it.withNormalizedAlpha() }
            if (newColorList.size == 1) {
                color = newColorList.first()
            } else {
                val x0 = 0f
                val y0 = 0f
                val x1 = 0f
                val y1 = shapeHeight

                val gradient = LinearGradient(
                    x0, y0, x1, y1,
                    newColorList.toIntArray(),
                    null,
                    Shader.TileMode.CLAMP
                )
                shader = gradient
            }
        }
    }
    canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), paint)
    return createBitmapOverlay(frameSize, offset, bitmap)
}

@OptIn(UnstableApi::class)
private fun TextLayer.toOverlay(frameSize: Size): TextureOverlay {
    val layer = this
    val text = layer.text
    val bitmap = createBitmap(layer.size.width, layer.size.height)

    val canvas = Canvas(bitmap)
    val textPaint = TextPaint().apply {
        color = layer.color.withNormalizedAlpha()
        isAntiAlias = true
        textSize = layer.fontSize.toFloat()
    }

    val staticLayout = StaticLayout.Builder.obtain(
        text,
        0,
        text.length,
        textPaint,
        bitmap.width,
    )
        .setLineSpacing(0f, 1f)
        .build()
    staticLayout.draw(canvas)

    return createBitmapOverlay(frameSize, layer.offset, bitmap)
}

@OptIn(UnstableApi::class)
private fun createBitmapOverlay(
    frameSize: Size,
    offset: LayerOffset,
    bitmap: Bitmap,
): BitmapOverlay {
    val (anchorX, anchorY) = calculateFrameAnchor(
        frameSize = frameSize,
        offset = offset,
        actualWidth = bitmap.width,
        actualSize = bitmap.height
    )

    return BitmapOverlay.createStaticBitmapOverlay(
        /* overlayBitmap = */ bitmap,
        /* overlaySettings = */ StaticOverlaySettings.Builder()
            .setBackgroundFrameAnchor(anchorX, anchorY)
            .build()
    )
}

/**
 * @return Pair of anchorX and anchorY in NDC coordinates
 */
private fun calculateFrameAnchor(
    frameSize: Size,
    offset: LayerOffset,
    actualWidth: Int,
    actualSize: Int,
): Pair<Float, Float> {
    if (offset == LayerOffset.Zero) return 0f to 0f

    val videoWidth = frameSize.width.toFloat()
    val videoHeight = frameSize.height.toFloat()

    val offsetX = offset.x
    val offsetY = offset.y

    // Convert offset to NDC coordinates
    val ndcX = (offsetX / videoWidth) * 2 - 1
    val ndcY = 1 - (offsetY / videoHeight) * 2

    // Adjust for bitmap size to get the anchor point
    val anchorX = ndcX + (actualWidth / videoWidth)
    val anchorY = ndcY - (actualSize / videoHeight)

    return anchorX to anchorY
}

private operator fun LayerSize.times(scale: Float): LayerSize =
    LayerSize((width * scale).roundToInt(), (height * scale).roundToInt())

private operator fun LayerOffset.times(scale: Float): LayerOffset =
    LayerOffset((x * scale).roundToInt(), (y * scale).roundToInt())

private const val NORMALIZED_ALPHA_THRESHOLD = 220

private fun Int.withNormalizedAlpha(): Int {
    val originalColor = this
    val alpha = alpha(originalColor)
    if (alpha >= NORMALIZED_ALPHA_THRESHOLD) return originalColor

    val newAlpha = (alpha(originalColor) * 1.5f).toInt().coerceAtMost(NORMALIZED_ALPHA_THRESHOLD)
    return android.graphics.Color.argb(
        newAlpha,
        red(originalColor), green(originalColor), blue(originalColor)
    )
}

private suspend fun <T, R> Iterable<T>.mapParallel(
    transform: suspend CoroutineScope.(T) -> R,
): List<R> = coroutineScope {
    map { item ->
        async { transform(item) }
    }.awaitAll()
}
