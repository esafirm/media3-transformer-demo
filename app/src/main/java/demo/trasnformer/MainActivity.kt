package demo.trasnformer

import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import demo.trasnformer.transformer.CanvasBitmapCreator
import demo.trasnformer.transformer.ComposeBitmapCreator
import demo.trasnformer.transformer.LayerCollection
import demo.trasnformer.transformer.LayerOffset
import demo.trasnformer.transformer.LayerSize
import demo.trasnformer.transformer.LayerToOverlay
import demo.trasnformer.transformer.LayerToOverlayImpl
import demo.trasnformer.transformer.MediaTransformer
import demo.trasnformer.transformer.ShapeLayer
import demo.trasnformer.transformer.TextLayer
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

    private val composeConverter: LayerToOverlay by lazy {
        LayerToOverlayImpl(bitmapCreator = ComposeBitmapCreator(applicationContext))
    }

    private val canvasConverter: LayerToOverlay by lazy {
        LayerToOverlayImpl(bitmapCreator = CanvasBitmapCreator())
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
                        var converter by remember { mutableStateOf<LayerToOverlay>(composeConverter) }
                        Column(
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(innerPadding)
                        ) {
                            RadioButtonSingleSelection { selectedIndex ->
                                converter = when (selectedIndex) {
                                    0 -> composeConverter
                                    1 -> canvasConverter
                                    else -> composeConverter
                                }
                            }
                            Content(screenWidth, converter)
                            Button(onClick = { export(screenWidth, converter) }) {
                                Text("Export")
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Content(size: Int, converter: LayerToOverlay) {
        val layerConfiguration = remember(size) { createLayer(size) }
        var bitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }

        LaunchedEffect(converter, layerConfiguration) {
            withContext(Dispatchers.IO) {
                val frameSize = Size(size, size)
                bitmaps = converter.toBitmap(layerConfiguration, frameSize, frameSize)
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Template(layer = layerConfiguration)

            if (bitmaps.isNotEmpty()) {
                BitmapTemplate(bitmaps = bitmaps)
            } else {
                CircularProgressIndicator()
            }
        }
    }

    @Composable
    private fun RadioButtonSingleSelection(
        modifier: Modifier = Modifier,
        onSelection: (Int) -> Unit,
    ) {
        val radioOptions = listOf("Compose", "Canvas")
        val (selectedOption, onOptionSelected) = remember { mutableStateOf(radioOptions[0]) }
        // Note that Modifier.selectableGroup() is essential to ensure correct accessibility behavior
        Column(modifier.selectableGroup()) {
            radioOptions.forEach { text ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .selectable(
                            selected = (text == selectedOption),
                            onClick = {
                                onOptionSelected(text)
                                onSelection(radioOptions.indexOf(text))
                            },
                        )
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (text == selectedOption),
                        onClick = null // null recommended for accessibility with screen readers
                    )
                    Text(
                        text = text,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
        }
    }

    private fun export(width: Int, converter: LayerToOverlay) = lifecycleScope.launch {
        val tempFile = File(cacheDir, "output.mp4")
        val audioFile = getFileFromAssets("audio.m4a")
        val size = Size(width, width)
        transformer.createVideo(
            audioUri = audioFile.toUri(),
            outputSize = size,
            outputFile = tempFile,
            overlays = converter.toOverlays(createLayer(width), size, size),
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

private fun createLayer(size: Int): LayerCollection {
    return LayerCollection(
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
                fontSize = 80
            ),
        )
    )
}

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
                            .width(with(density) { it.size.width.toDp() })
                            .height(with(density) { it.size.height.toDp() })
                            .background(brush = Brush.verticalGradient(colors = colors))
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

@Composable
fun BitmapTemplate(
    bitmaps: List<Bitmap>,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        bitmaps.forEach { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
