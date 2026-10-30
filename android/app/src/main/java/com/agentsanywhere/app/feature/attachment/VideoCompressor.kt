package com.agentsanywhere.app.feature.attachment

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

data class CompressedVideo(
    val file: File?,
    val name: String,
    val sourceSize: Long,
)

/**
 * Best-effort H.264 transcode for large screen recordings. Videos that cannot be
 * decoded by the device are returned as-is so callers can surface the normal
 * upload-size error instead of losing the user's file.
 */
object VideoCompressor {
    private const val MAX_EDGE = 1280
    private const val TARGET_BITRATE = 1_500_000
    private const val TARGET_BYTES = 24L * 1024 * 1024

    suspend fun compress(
        context: Context,
        uri: Uri,
        name: String,
        sourceSize: Long,
    ): CompressedVideo = withContext(Dispatchers.IO) {
        if (sourceSize <= TARGET_BYTES) return@withContext CompressedVideo(null, name, sourceSize)
        val output = File(context.cacheDir, "video_tmp/${UUID.randomUUID()}.mp4").apply {
            parentFile?.mkdirs()
        }
        val result = runCatching { transcode(context, uri, output) }.getOrNull()
        if (result == null || !output.isFile || output.length() <= 0L || output.length() > TARGET_BYTES) {
            output.delete()
            return@withContext CompressedVideo(null, name, sourceSize)
        }
        CompressedVideo(output, name.substringBeforeLast('.').ifBlank { "video" } + "-compressed.mp4", sourceSize)
    }

    private fun transcode(context: Context, uri: Uri, output: File) {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)
        val videoTrack = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)
                ?.startsWith("video/") == true
        } ?: error("No video track")

        val inputFormat = extractor.getTrackFormat(videoTrack)
        val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: error("No video mime")
        val sourceWidth = inputFormat.getInteger(MediaFormat.KEY_WIDTH)
        val sourceHeight = inputFormat.getInteger(MediaFormat.KEY_HEIGHT)
        val scale = minOf(1f, MAX_EDGE.toFloat() / maxOf(sourceWidth, sourceHeight).toFloat())
        val outWidth = ((sourceWidth * scale).roundToInt() / 2 * 2).coerceAtLeast(2)
        val outHeight = ((sourceHeight * scale).roundToInt() / 2 * 2).coerceAtLeast(2)
        val rotation = runCatching { inputFormat.getInteger("rotation-degrees") }.getOrDefault(0)
        val durationUs = runCatching { inputFormat.getLong(MediaFormat.KEY_DURATION) }.getOrDefault(0L)
        val frameRate = runCatching { inputFormat.getInteger(MediaFormat.KEY_FRAME_RATE) }
            .getOrDefault(30).coerceIn(15, 60)

        val decoder = MediaCodec.createDecoderByType(mime)
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val encoderFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, outWidth, outHeight).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, TARGET_BITRATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        if (rotation != 0) muxer.setOrientationHint(rotation)
        var videoTrackIndex = -1
        var started = false
        val info = MediaCodec.BufferInfo()
        try {
            encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = encoder.createInputSurface()
            encoder.start()
            decoder.configure(inputFormat, surface, null, 0)
            decoder.start()
            extractor.selectTrack(videoTrack)

            var inputDone = false
            var decoderDone = false
            var encoderDone = false
            while (!encoderDone) {
                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                if (!decoderDone) {
                    val outIndex = decoder.dequeueOutputBuffer(info, 10_000)
                    if (outIndex >= 0) {
                        val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        decoder.releaseOutputBuffer(outIndex, true)
                        if (eos) {
                            decoderDone = true
                            encoder.signalEndOfInputStream()
                        }
                    }
                }
                val encIndex = encoder.dequeueOutputBuffer(info, 10_000)
                when {
                    encIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!started) {
                            videoTrackIndex = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            started = true
                        }
                    }
                    encIndex >= 0 -> {
                        val encoded = encoder.getOutputBuffer(encIndex)
                        if (encoded != null && info.size > 0 && started) {
                            encoded.position(info.offset)
                            encoded.limit(info.offset + info.size)
                            muxer.writeSampleData(videoTrackIndex, encoded, info)
                        }
                        encoder.releaseOutputBuffer(encIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) encoderDone = true
                    }
                }
                if (durationUs > 0 && info.presentationTimeUs > durationUs + 10_000_000L) {
                    encoder.signalEndOfInputStream()
                }
            }
        } finally {
            runCatching { decoder.stop() }
            runCatching { decoder.release() }
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
            runCatching { if (started) muxer.stop() }
            runCatching { muxer.release() }
            runCatching { extractor.release() }
        }
    }
}
