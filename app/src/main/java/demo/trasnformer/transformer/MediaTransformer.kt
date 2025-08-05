package demo.trasnformer.transformer

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Size
import androidx.annotation.OptIn
import androidx.core.graphics.createBitmap
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.TextureOverlay
import androidx.media3.exoplayer.MediaExtractorCompat
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.Transformer
import com.bandlab.videoprocessor.utils.media3.TransformerInput
import com.bandlab.videoprocessor.utils.media3.TransformerStatus
import com.bandlab.videoprocessor.utils.media3.buildWith
import com.bandlab.videoprocessor.utils.media3.start
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds

internal const val FRAME_RATE_30_FPS = 30

@UnstableApi
class MediaTransformer(
    private val app: Application,
    private val videoCache: File
) {

    private val transformer: Transformer by lazy {
        Transformer.Builder(app).buildWith {
            setVideoMimeType(MimeTypes.VIDEO_H264)
            setAudioMimeType(MimeTypes.AUDIO_AAC)
        }
    }

    init {
        if (!videoCache.exists()) {
            videoCache.mkdirs()
        }
    }

    /**
     * Create video from overlays and audio.
     *
     * @param audioUri the audio uri to merge
     * @param outputSize the preferred size of the video
     * @param outputFile the path to save the merged video
     * @param overlays the list of overlays to add to the video. It requires at least one overlay.
     * @param onProgress a lambda that will be called with the progress. It range from 0 to 100
     */
    suspend fun createVideo(
        audioUri: Uri,
        outputSize: Size,
        outputFile: File,
        overlays: List<TextureOverlay>,
        onProgress: (Int) -> Unit,
    ) = withContext(Dispatchers.Default) {
        require(overlays.isNotEmpty()) { "Overlays must not be empty" }

        val audioDuration = audioUri.getAudioDuration(app)
        val video =
            overlays.toVideoEditMediaItem(targetSize = outputSize, duration = audioDuration)

        val composition = Composition.Builder(
            itemSequenceOf(video),
            itemSequenceOf(audioUri.toEditMediaItem()),
        ).build()

        transformer.start(
            input = TransformerInput.Composition(composition),
            output = outputFile,
            onProgress = onProgress
        ).throwIfFailed()
    }

    private fun List<TextureOverlay>.toVideoEditMediaItem(
        targetSize: Size,
        duration: Duration,
    ): EditedMediaItem {
        val targetFile = File(videoCache, "temp_video_canvas.jpg")
        val placeholderBitmap =
            createBitmap(targetSize.width, targetSize.height, Bitmap.Config.RGB_565)

        targetFile.outputStream().use { stream ->
            placeholderBitmap.compress(Bitmap.CompressFormat.JPEG, 1, stream)
        }

        val videoCanvas = MediaItem.Builder()
            .setUri(Uri.fromFile(targetFile))
            .setMimeType(MimeTypes.IMAGE_JPEG)
            .build()

        val effects = listOf(
            OverlayEffect(this),
            Presentation.createForWidthAndHeight(
                /* width = */ targetSize.width,
                /* height = */ targetSize.height,
                /* layout = */ Presentation.LAYOUT_STRETCH_TO_FIT,
            )
        )

        return EditedMediaItem.Builder(videoCanvas)
            .setDurationUs(duration.inWholeMicroseconds)
            .setFrameRate(FRAME_RATE_30_FPS)
            .setEffects(Effects(emptyList(), effects.toList()))
            .build()
    }
}


internal fun Uri.getAudioDuration(context: Context): Duration =
    mediaDurationOrNull(context)
        ?: extractAudioDuration(context)
        ?: error("Failed to extract audio duration from $this")

internal fun Uri.mediaDurationOrNull(context: Context): Duration? {
    val retriever = MediaMetadataRetriever()

    return try {
        retriever.setDataSource(context, this)
        mediaDuration(retriever)
    } catch (_: Exception) {
        println("Not able to extract video duration from $this")
        null
    } finally {
        retriever.release()
    }
}

/**
 * Extract audio duration from the given [Uri] using [MediaExtractorCompat].
 */
@OptIn(UnstableApi::class)
private fun Uri.extractAudioDuration(context: Context): Duration? {
    println("MediaTransformer:: using MediaExtractor to extract duration")

    val uri = this
    val extractor = MediaExtractorCompat(context).apply { setDataSource(context, uri, null) }
    with(extractor) {
        for (i in 0 until trackCount) {
            val format = getTrackFormat(i)
            if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                selectTrack(i)
                release()
                return format.getLong(MediaFormat.KEY_DURATION).microseconds
            }
        }
        release()
    }
    return null
}

private fun mediaDuration(retriever: MediaMetadataRetriever): Duration {
    val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        ?.toLongOrNull()
        ?: error("Failed to get video duration")

    return durationMs.milliseconds
}

internal fun TransformerStatus.Finished.throwIfFailed() {
    when (this) {
        is TransformerStatus.Failure -> throw this.error
        is TransformerStatus.Success -> Unit // do nothing
    }
}

@OptIn(UnstableApi::class)
internal fun itemSequenceOf(item: EditedMediaItem): EditedMediaItemSequence {
    return EditedMediaItemSequence.Builder(item).build()
}

@OptIn(UnstableApi::class)
internal fun itemSequenceOf(items: List<EditedMediaItem>): EditedMediaItemSequence {
    return EditedMediaItemSequence.Builder(items).build()
}

@OptIn(UnstableApi::class)
internal fun Uri.toEditMediaItem(): EditedMediaItem {
    return EditedMediaItem.Builder(MediaItem.fromUri(this)).build()
}
