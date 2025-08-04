package demo.trasnformer

import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentValues
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import demo.trasnformer.transformer.LayerConfiguration
import demo.trasnformer.transformer.LayerOffset
import demo.trasnformer.transformer.LayerSize
import demo.trasnformer.transformer.MediaTransformer
import demo.trasnformer.transformer.ShapeLayer
import demo.trasnformer.transformer.TextLayer
import demo.trasnformer.transformer.toOverlays
import demo.trasnformer.ui.theme.DemoTransformerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@UnstableApi
class MainActivity : ComponentActivity() {

    private val transformer by lazy {
        MediaTransformer(
            applicationContext as Application,
            File(applicationContext.filesDir, "VideoCache")
        )
    }

    @SuppressLint("UnusedBoxWithConstraintsScope")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DemoTransformerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BoxWithConstraints {
                        val screenWidth = with(LocalDensity.current) { maxWidth.toPx() }.toInt()
                        Column(modifier = Modifier.padding(innerPadding)) {
                            Content(screenWidth)
                            Button(onClick = { export(screenWidth) }) {
                                Text("Export")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun export(width: Int) = lifecycleScope.launch {
        val tempFile = File(cacheDir, "output.mp4")
        val audioFile = getFileFromAssets("audio.m4a")
        val size = Size(width, width)
        transformer.createVideo(
            audioUri = audioFile.toUri(),
            outputSize = size,
            outputFile = tempFile,
            overlays = createLayer(width).toOverlays(size, size),
            onProgress = {
                println("Export progress: $it")
            }
        )

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "output.mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }

        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                tempFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
        tempFile.delete()

        println("Export finished to Downloads")
        withContext(Dispatchers.Main) {
            Toast.makeText(this@MainActivity, "Export finished to Downloads", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun getFileFromAssets(fileName: String): File {
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
}

private fun createLayer(size: Int): LayerConfiguration {
    return LayerConfiguration(
        layers = listOf(
            ShapeLayer(
                offset = LayerOffset(0, 0),
                size = LayerSize(size, size),
                colorList = listOf(
                    Color.Magenta.copy(alpha = 0.5f).toArgb(),
                    Color.Magenta.toArgb(),
                )
            ),
            TextLayer(
                offset = LayerOffset(0, 0),
                size = LayerSize(size, size),
                text = "This is a text",
                color = Color.Black.copy(alpha = 0.5f).toArgb(),
                lineHeight = 100,
            ),
        )
    )
}

@Composable
fun Content(size: Int) {
    Template(createLayer(size))
}

@Composable
fun Template(layer: LayerConfiguration) {
    val density = LocalDensity.current
    Box {
        layer.layers.forEach {
            when (it) {
                is ShapeLayer -> {
                    val colors = it.colorList.map(::Color)
                    Spacer(
                        modifier = Modifier
                            .width(with(density) { it.size.width.toDp() })
                            .height(with(density) { it.size.height.toDp() })
                            .background(brush = Brush.verticalGradient(colors = colors))
                    )
                }

                is TextLayer -> {
                    val fontSize = with(LocalDensity.current) { (it.fontSize / fontScale).toSp() }
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

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    DemoTransformerTheme {
        Content(360)
    }
}
