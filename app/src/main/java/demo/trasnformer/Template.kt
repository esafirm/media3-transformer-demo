package demo.trasnformer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import demo.trasnformer.transformer.ClipShape
import demo.trasnformer.transformer.Layer
import demo.trasnformer.transformer.LayerCollection
import demo.trasnformer.transformer.LayerSize
import demo.trasnformer.transformer.Offset
import demo.trasnformer.transformer.Shadow
import demo.trasnformer.transformer.TextLayer
import demo.trasnformer.transformer.LayerSize as Size
import demo.trasnformer.transformer.ShapeLayer as Shape

class Template {
    private val textColor = Color.White
    private val dominantContainerBackgroundGradient get() = Color.Magenta
    private val templateShape get() = ClipShape.RoundedRectangle(radius = 60)

    fun prepare(withBackground: Boolean): LayerCollection {
        val layers = mutableListOf<() -> Layer>().apply {
            if (withBackground) {
                val templateContainerShape = ClipShape.RoundedRectangle(radius = 48)
                // Layer1
                add {
                    Shape.solidColor(
                        Offset.Zero,
                        LayerSize.FullScreen,
                        color = 0xFF101214.toInt(),
                        clipShape = templateContainerShape
                    )
                }

                // Layer2 - Container Gradient
                val containerBackgroundGradient = dominantContainerBackgroundGradient
                val colors = listOf(
                    containerBackgroundGradient.copy(alpha = 0.5f).toArgb(),
                    Color.Unspecified.toArgb(),
                )
                add {
                    Shape(
                        Offset.Zero,
                        LayerSize.FullScreen,
                        colorList = colors,
                        clipShape = templateContainerShape
                    )
                }
            }
            val templateBaseOffset = if (withBackground) Offset(180, 348) else Offset.Zero
            addAll(buildTemplateRenderCallbacks(templateBaseOffset))
        }
        return LayerCollection(layers.map { it() })
    }

    private fun buildTemplateRenderCallbacks(baseOffset: Offset): List<() -> Layer> {
        return mutableListOf<() -> Layer>().apply {
            // Layer3 -- track_template_container_border
            add {
                Shape.solidColor(
                    offset = baseOffset,
                    size = LayerSize(724, 1228),
                    color = 0x29C2D7F2,
                    clipShape = templateShape,
                )
            }
            add {
                // track_template_container
                Shape.solidColor(
                    offset = baseOffset + Offset(2, 2),
                    size = Size(720, 1224),
                    color = 0xFF010101.toInt(),
                    clipShape = templateShape,
                    shadow = Shadow(
                        relativeOffset = Offset(3, 100),
                        clipShape = templateShape,
                        color = 0x66000000,
                        blurRadius = 120f,
                    ),
                )
            }

            // Layer4 -- track_template_container_gradient
            val containerBackgroundGradient = dominantContainerBackgroundGradient
            val colors = listOf(
                containerBackgroundGradient.toArgb(),
                containerBackgroundGradient.copy(alpha = 0.3f).toArgb(),
            )
            add {
                Shape(
                    offset = baseOffset + Offset(2, 2),
                    size = Size(720, 1224),
                    colorList = colors,
                    clipShape = templateShape,
                )
            }

            // Layer5
            addRevisionCoverLayer(baseOffset)

            val isExplicit = true
            if (isExplicit) {
                add {
                    // explicit_icon_background
                    Shape.solidColor(
                        offset = baseOffset + Offset(54, 54),
                        size = Size(46),
                        color = Color.Black.copy(alpha = 0.3f).toArgb(),
                        clipShape = ClipShape.RoundedRectangle(8),
                    )
                }
                add {
                    Shape.solidColor(
                        offset = baseOffset + Offset(54, 54),
                        size = Size(47),
                        color = Color.White.toArgb(),
                        clipShape = ClipShape.RoundedRectangle(8),
                    )
                }
            }

            val textColor = textColor
            add {
                TextLayer(
                    offset = baseOffset + Offset(40, 720),
                    size = Size(640, 120),
                    text = "A revision name",
                    color = textColor.toArgb(),
                    lineHeight = 67,
                    fontSize = 56,
                )
            }

            val authorTextColor = textColor
            add {
                TextLayer(
                    offset = baseOffset + Offset(40, 736 + 67),
                    size = Size(640, 80),
                    text = "An Author Name",
                    color = authorTextColor.toArgb(),
                    lineHeight = 40,
                    fontSize = 40,
                )
            }

            // Layer6
            add {
                Shape.solidColor(
                    offset = baseOffset + Offset(40, 1116),
                    size = Size(640, 48),
                    color = Color.Red.copy(alpha = 0.5f).toArgb(),
                    clipShape = ClipShape.Circle,
                )
            }
        }
    }

    private fun MutableList<() -> Layer>.addRevisionCoverLayer(baseOffset: Offset) {
        add {
            // "track_template_default_revision_background"
            Shape.solidColor(
                color = 0xFF101214.toInt(),
                offset = baseOffset + Offset(30, 30),
                size = Size(660),
                clipShape = ClipShape.RoundedRectangle(radius = 40),
            )
        }
        add {
            // App logo
            Shape.solidColor(
                offset = baseOffset + Offset(138, 320),
                size = Size(445, 80),
                color = Color.Red.copy(alpha = 0.5f).toArgb(),
                clipShape = ClipShape.Circle,
            )
        }
        add {
            // Trademark
            Shape.solidColor(
                offset = baseOffset + Offset(581, 376),
                size = Size(13, 13),
                color = Color.Red.copy(alpha = 0.5f).toArgb(),
                clipShape = ClipShape.Rectangle,
            )
        }
    }
}
