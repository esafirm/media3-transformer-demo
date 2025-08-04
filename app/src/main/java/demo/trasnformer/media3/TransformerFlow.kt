package com.bandlab.videoprocessor.utils.media3

import androidx.annotation.CheckResult
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val DEFAULT_PROGRESS_POLL_DELAY_MS = 500L

/**
 * Build upon an existing [Transformer.Builder] instance.
 *
 * @param[block] The block to use to configure the [Transformer.Builder].
 * @return The [Transformer] created from this [Transformer.Builder].
 */
fun Transformer.Builder.buildWith(
    block: Transformer.Builder.() -> Unit,
): Transformer = apply(block).build()


/**
 * Build upon an existing [Transformer] instance.
 *
 * @param[block] The block to use to configure the [Transformer.Builder].
 * @return The [Transformer] created from this [Transformer.Builder].
 */
fun Transformer.buildWith(
    block: Transformer.Builder.() -> Unit,
): Transformer = buildUpon().apply(block).build()

/**
 * Converts a [Transformer] to a [Flow] that emits [TransformerStatus].
 *
 * All existing listeners on [Transformer] will be removed and replaced with a new listener that
 * converts the updates to a [Flow].
 *
 * **Note:** This must flow on [Dispatchers.Main] as [Transformer] expects a Looper.
 *
 * @receiver The [Transformer] instance to start a transformation.
 * @param[input] The input to transform.
 * @param[output] The output file to write to.
 * @param[progressPollDelayMs] The delay between polling for progress.
 * @return A [Flow] that emits [TransformerStatus].
 */
internal fun Transformer.createTransformerCallbackFlow(
    input: TransformerInput,
    output: File,
    progressPollDelayMs: Long = DEFAULT_PROGRESS_POLL_DELAY_MS,
): Flow<TransformerStatus> {
    val oldTransformer = this
    return callbackFlow {
        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                trySend(TransformerStatus.Success(output, exportResult))
                close()
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException,
            ) {
                trySend(TransformerStatus.Failure(exportException))
                close()
            }
        }

        val transformer = oldTransformer
            .buildWith {
                removeAllListeners()
                addListener(listener)
            }
            .startWith(input, output)

        trySend(TransformerStatus.Progress(0))

        val progressHolder = ProgressHolder()
        var progressState = Transformer.PROGRESS_STATE_NOT_STARTED
        var previousProgress = 0
        val progressJob = launch(Dispatchers.Default) {
            while (isActive && progressState != Transformer.PROGRESS_STATE_UNAVAILABLE) {
                progressState = withContext(Dispatchers.Main) {
                    transformer.getProgress(progressHolder)
                }

                if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                    val progress = progressHolder.progress
                    if (progress > previousProgress) {
                        previousProgress = progress
                        trySend(TransformerStatus.Progress(progress))
                    }
                } else if (progressState == Transformer.PROGRESS_STATE_UNAVAILABLE) {
                    break
                }

                delay(progressPollDelayMs)
            }
        }

        awaitClose {
            progressJob.cancel()
            transformer.cancel()
        }
    }.catch { cause ->
        if (cause != CancellationException()) {
            emit(TransformerStatus.Failure(cause))
        }
    }.flowOn(Dispatchers.Main)
}

/**
 * Map an [TransformerInput] into a value that [Transformer] can use.
 */
private fun Transformer.startWith(input: TransformerInput, output: File): Transformer = apply {
    val outputPath = output.absolutePath
    when (input) {
        is TransformerInput.MediaItem -> start(input.value, outputPath)
        is TransformerInput.EditedMediaItem -> start(input.value, outputPath)
        is TransformerInput.Uri -> start(MediaItem.fromUri(input.value), outputPath)
        is TransformerInput.File -> {
            start(MediaItem.fromUri(input.value.toUri()), outputPath)
        }

        is TransformerInput.Composition -> start(input.value, outputPath)
    }
}

/**
 * Start a [Transformer] request and return a [Flow] of [TransformerStatus].
 *
 * @see createTransformerCallbackFlow
 * @param[input] The input to transform.
 * @param[output] The output file to write to.
 * @param[progressPollDelayMs] The delay between polling for progress.
 * @return A [Flow] that emits [TransformerStatus].
 */
@CheckResult
internal fun Transformer.start(
    input: TransformerInput,
    output: File,
    progressPollDelayMs: Long = DEFAULT_PROGRESS_POLL_DELAY_MS,
): Flow<TransformerStatus> = createTransformerCallbackFlow(
    input = input,
    output = output,
    progressPollDelayMs = progressPollDelayMs,
)

/**
 * Start a [Transformer] request in a coroutine and return a [TransformerStatus.Finished]
 * when the request is finished.
 *
 * For progress updates pass a [onProgress] callback.
 *
 * @see start
 * @param[input] The input to transform.
 * @param[output] The output file to write to.
 * @param[progressPollDelayMs] The delay between polling for progress.
 * @param[onProgress] The callback to use for progress updates.
 * @return A [TransformerStatus.Finished] status.
 */
internal suspend fun Transformer.start(
    input: TransformerInput,
    output: File,
    progressPollDelayMs: Long = DEFAULT_PROGRESS_POLL_DELAY_MS,
    onProgress: (Int) -> Unit = {},
): TransformerStatus.Finished {
    try {
        val result: TransformerStatus? = start(
            input = input,
            output = output,
            progressPollDelayMs = progressPollDelayMs,
        ).onEach { status ->
            if (status is TransformerStatus.Progress) {
                onProgress(status.progress)
            }
        }.lastOrNull()

        if (result == null || result !is TransformerStatus.Finished) {
            error("Unexpected finish result: $result")
        }

        return result
    } catch (e: CancellationException) {
        throw e
    } catch (error: Throwable) {
        return TransformerStatus.Failure(error)
    }
}