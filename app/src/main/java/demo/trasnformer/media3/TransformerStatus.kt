package com.bandlab.videoprocessor.utils.media3

import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File

sealed interface TransformerStatus {

    /**
     * Denotes the completion of a [Transformer] execution.
     */
    sealed interface Finished : TransformerStatus

    /**
     * Current progress of a [Transformer] execution.
     *
     * @param[progress] Integer progress value between 0-100
     */
    class Progress(
        val progress: Int,
    ) : TransformerStatus

    /**
     * A successful [Transformer] execution.
     *
     * @param[output] The output [File] of the [Transformer] execution.
     */
    class Success(
        val output: File,
        val exportResult: ExportResult,
    ) : TransformerStatus, Finished

    /**
     * [Transformer] encountered a failure.
     *
     * @param[error] The [Throwable] that caused the failure.
     */
    class Failure(val error: Throwable) : TransformerStatus, Finished
}