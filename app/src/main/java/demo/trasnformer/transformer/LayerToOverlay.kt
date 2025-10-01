package demo.trasnformer.transformer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Size
import android.util.SizeF
import androidx.annotation.OptIn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.createBitmap
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextureOverlay
import demo.trasnformer.Template
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

data class LayerCollection(val layers: List<Layer>)

interface LayerToOverlay {

    @OptIn(UnstableApi::class)
    suspend fun toOverlays(
        collection: LayerCollection,
        frameSize: Size,
        originalFrameSize: Size
    ): List<TextureOverlay>

    suspend fun toBitmap(
        collection: LayerCollection,
        frameSize: Size,
        originalFrameSize: Size
    ): List<Bitmap>
}

interface BitmapCreator {
    suspend fun create(layer: Layer): Bitmap
}

@OptIn(UnstableApi::class)
class LayerToOverlayImpl(
    private val bitmapCreator: BitmapCreator
) : LayerToOverlay {

    override suspend fun toOverlays(
        collection: LayerCollection,
        frameSize: Size,
        originalFrameSize: Size
    ): List<TextureOverlay> {
        return collection.process(frameSize, originalFrameSize) { bitmap, layer ->
            createBitmapOverlay(frameSize, layer.offset, bitmap)
        }
    }

    override suspend fun toBitmap(
        collection: LayerCollection,
        frameSize: Size,
        originalFrameSize: Size
    ): List<Bitmap> {
        return collection.process(frameSize, originalFrameSize) { bitmap, _ -> bitmap }
    }

    private suspend fun <T> LayerCollection.process(
        frameSize: Size,
        originalFrameSize: Size,
        block: (Bitmap, Layer) -> T
    ): List<T> {
        val scale = max(
            frameSize.width.toFloat() / originalFrameSize.width,
            frameSize.height.toFloat() / originalFrameSize.height
        )
        return adjustedLayers(scale).mapParallel { layer ->
            println("--> Creating bitmap for $layer")

            val bitmap = bitmapCreator.create(layer)
            block(bitmap, layer)
        }
    }
}

class CanvasBitmapCreator : BitmapCreator {
    override suspend fun create(layer: Layer): Bitmap {
        return when (layer) {
            is ShapeLayer -> layer.toBitmap()
            is TextLayer -> layer.toBitmap()
        }
    }
}

class ComposeBitmapCreator(private val appContext: Context) : BitmapCreator {
    override suspend fun create(layer: Layer): Bitmap {
        return withContext(Dispatchers.Main.immediate) {
            useVirtualDisplay(appContext) { display ->
                captureComposable(
                    context = appContext,
                    size = IntSize(layer.size.width, layer.size.height),
                    display = display
                ) {
                    Template(LayerCollection(layers = listOf(layer)))
                    LaunchedEffect(Unit) {
                        capture()
                    }
                }
            }.asAndroidBitmap()
        }
    }
}

/**
 * Adjusts the size and offset of each layer in the configuration based on the provided scale.
 *
 * @param scale The scale factor to adjust the layers.
 * @return A list of adjusted layers.
 */
private fun LayerCollection.adjustedLayers(scale: Float) = layers.map {
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
    val bitmap = toBitmap()
    return createBitmapOverlay(frameSize, offset, bitmap)
}

private fun ShapeLayer.toBitmap(): Bitmap {
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

    // Clipping
    val path = Path()
    when (val clipShape = layer.clipShape) {
        is ClipShape.Circle -> {
            path.addOval(0f, 0f, shapeWidth, shapeHeight, Path.Direction.CW)
        }
        is ClipShape.Rectangle -> {
            // No path needed for rectangle clipping
        }
        is ClipShape.RoundedRectangle -> {
            path.addRoundRect(
                RectF(0f, 0f, shapeWidth, shapeHeight),
                floatArrayOf(
                    clipShape.topLeftRadius.toFloat(), clipShape.topLeftRadius.toFloat(),
                    clipShape.topRightRadius.toFloat(), clipShape.topRightRadius.toFloat(),
                    clipShape.bottomRightRadius.toFloat(), clipShape.bottomRightRadius.toFloat(),
                    clipShape.bottomLeftRadius.toFloat(), clipShape.bottomLeftRadius.toFloat()
                ),
                Path.Direction.CW
            )
        }
    }
    if (!path.isEmpty) {
        canvas.clipPath(path)
    }

    val paint = Paint().apply {
        isAntiAlias = true
        val colorList = layer.colorList
        if (colorList.isNotEmpty()) {
            if (colorList.size == 1) {
                color = colorList.first()
            } else {
                val x0 = 0f
                val y0 = 0f
                val x1 = 0f
                val y1 = shapeHeight

                val gradient = LinearGradient(
                    x0, y0, x1, y1,
                    colorList.toIntArray(),
                    null,
                    Shader.TileMode.CLAMP
                )
                shader = gradient
            }
        }
    }
    canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), paint)
    return bitmap
}

@OptIn(UnstableApi::class)
private fun TextLayer.toOverlay(frameSize: Size): TextureOverlay {
    val bitmap = toBitmap()
    return createBitmapOverlay(frameSize, offset, bitmap)
}

private fun TextLayer.toBitmap(): Bitmap {
    val layer = this
    val text = layer.text
    val bitmap = createBitmap(layer.size.width, layer.size.height)

    val canvas = Canvas(bitmap)
    val textPaint = TextPaint().apply {
        color = layer.color
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

    return bitmap
}

@OptIn(UnstableApi::class)
private fun createBitmapOverlay(
    frameSize: Size,
    offset: Offset,
    bitmap: Bitmap,
): BitmapOverlay {
    val (anchorX, anchorY) = calculateFrameAnchor(
        frameSize = frameSize,
        offset = offset,
        actualWidth = bitmap.width,
        actualSize = bitmap.height
    )

    return BitmapOverlay.createStaticBitmapOverlay(
        /* overlayBitmap = */
        bitmap,
        /* overlaySettings = */
        StaticOverlaySettings.Builder()
            .setBackgroundFrameAnchor(anchorX, anchorY)
            .build()
    )
}

/**
 * @return Pair of anchorX and anchorY in NDC coordinates
 */
private fun calculateFrameAnchor(
    frameSize: Size,
    offset: Offset,
    actualWidth: Int,
    actualSize: Int,
): Pair<Float, Float> {
    if (offset == Offset.Zero) return 0f to 0f

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

private operator fun Offset.times(scale: Float): Offset =
    Offset((x * scale).roundToInt(), (y * scale).roundToInt())

private suspend fun <T, R> Iterable<T>.mapParallel(
    transform: suspend CoroutineScope.(T) -> R,
): List<R> = coroutineScope {
    map { item ->
        async { transform(item) }
    }.awaitAll()
}
