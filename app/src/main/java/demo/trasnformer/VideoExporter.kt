package demo.trasnformer

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.TextureOverlay
import demo.trasnformer.transformer.LayerCollection
import demo.trasnformer.transformer.LayerSize
import demo.trasnformer.transformer.MediaTransformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@UnstableApi
class VideoExporter(
    private val transformer: MediaTransformer,
    private val activity: ComponentActivity,
) {

    @RequiresApi(Build.VERSION_CODES.Q)
    fun export(
        layerCollection: LayerCollection,
        onGetOverlays: suspend (Size) -> List<TextureOverlay>
    ) = activity.lifecycleScope.launch {

        val size = Size(LayerSize.FullScreen.width, LayerSize.FullScreen.height)
        val overlays = onGetOverlays(size)

        createOutput(
            setting = ExportSetting(
                useMergedOverlay = false,
                outputPrefix = "separate"
            ), layerCollection, overlays, size
        )

        createOutput(
            setting = ExportSetting(
                useMergedOverlay = true,
                outputPrefix = "merged"
            ), layerCollection, overlays, size
        )

        println("Export finished to Downloads")
        withContext(Dispatchers.Main) {
            Toast.makeText(activity, "Export finished to Downloads", Toast.LENGTH_SHORT)
                .show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun createOutput(
        setting: ExportSetting,
        layerCollection: LayerCollection,
        overlays: List<TextureOverlay>,
        size: Size
    ) {
        val outputName = "${setting.outputPrefix}-output.mp4"
        val outputFile = File(activity.cacheDir, outputName)
        val audioFile = getFileFromAssets("audio.m4a")

        val passedOverlays = if (setting.useMergedOverlay) {
            listOf(overlays.mergeBitmapOverlay(layerCollection, size))
        } else {
            overlays
        }

        transformer.createVideo(
            audioUri = audioFile.toUri(),
            outputSize = size,
            outputFile = outputFile,
            overlays = passedOverlays,
            onProgress = { println("Export progress: $it") }
        )

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, outputName)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }

        val contentResolver = activity.contentResolver
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
        outputFile.delete()
    }

    private fun List<TextureOverlay>.mergeBitmapOverlay(
        layerCollection: LayerCollection,
        size: Size,
    ): BitmapOverlay {
        val bitmapOverlays = this.filterIsInstance<BitmapOverlay>()
        if (bitmapOverlays.size != this.size) {
            error("Only BitmapOverlay is supported for merging, found ${this.map { it::class.java }}")
        }

        val base = createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(base)

        bitmapOverlays.forEachIndexed { index, it ->
            val layer = layerCollection.layers[index]
            canvas.drawBitmap(
                /* bitmap = */ it.getBitmap(0),
                /* left = */ layer.offset.x.toFloat(),
                /* top = */ layer.offset.y.toFloat(),
                /* paint = */ null
            )
        }

        return BitmapOverlay.createStaticBitmapOverlay(base)
    }

    private fun getFileFromAssets(fileName: String): File {
        val applicationContext = activity.applicationContext
        val assetManager = applicationContext.assets
        val file = File(applicationContext.filesDir, fileName)
        if (file.exists()) {
            file.delete()
        }
        assetManager.open(fileName).use { inputStream ->
            file.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        return file
    }

    data class ExportSetting(
        val useMergedOverlay: Boolean,
        val outputPrefix: String,
    )

}
