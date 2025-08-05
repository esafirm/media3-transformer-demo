package demo.trasnformer

import android.annotation.SuppressLint
import android.app.Application
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import demo.trasnformer.transformer.CanvasBitmapCreator
import demo.trasnformer.transformer.ComposeBitmapCreator
import demo.trasnformer.transformer.LayerSize
import demo.trasnformer.transformer.LayerToOverlay
import demo.trasnformer.transformer.LayerToOverlayImpl
import demo.trasnformer.transformer.MediaTransformer
import demo.trasnformer.ui.theme.DemoTransformerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@UnstableApi
class MainActivity : ComponentActivity() {

    private val template by lazy { Template() }

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

    private val exporter by lazy { VideoExporter(transformer, this) }

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("UnusedBoxWithConstraintsScope")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DemoTransformerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BoxWithConstraints {
                        val screenWidth = with(LocalDensity.current) { maxWidth.toPx() }.toInt()
                        var converter by remember { mutableStateOf(composeConverter) }
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
//                            Content(screenWidth, converter)
                            Button(onClick = { export(converter) }) {
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
        val layers = remember(size) { template.prepare(true) }
        var bitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }

        LaunchedEffect(converter, layers) {
            withContext(Dispatchers.IO) {
                val size = Size(LayerSize.FullScreen.width, LayerSize.FullScreen.height)
                bitmaps = converter.toBitmap(layers, size, size)
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
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

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun export(converter: LayerToOverlay) {
        val layers = template.prepare(true)
        exporter.export(
            layerCollection = layers,
            onGetOverlays = { converter.toOverlays(layers, it, it) }
        )
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
