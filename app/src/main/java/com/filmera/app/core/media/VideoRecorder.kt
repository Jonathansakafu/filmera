package com.filmera.app.core.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import android.view.Surface
import java.io.File

/**
 * Records filtered frames to an MP4 file. Frames arrive through [surface], which the GL
 * pipeline draws into. The surface is landscape-shaped and the file carries a 90° rotation
 * hint, which every encoder supports (some reject portrait-shaped sizes).
 */
class VideoRecorder(private val context: Context) {

    class Session(
        val file: File,
        val surface: Surface,
        val width: Int,
        val height: Int,
        val rotation: Int,
        internal val recorder: MediaRecorder,
        val startedAt: Long = System.currentTimeMillis(),
    )

    fun start(
        file: File,
        uprightWidth: Int,
        uprightHeight: Int,
        fps: Int,
        bitrate: Int,
        withAudio: Boolean,
    ): Session {
        // Stored landscape; playback rotates 90° to upright portrait.
        val width = evenUp(maxOf(uprightWidth, uprightHeight))
        val height = evenUp(minOf(uprightWidth, uprightHeight))
        val rotation = if (uprightHeight > uprightWidth) 90 else 0

        val recorder = newRecorder()
        try {
            if (withAudio) recorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setOutputFile(file.absolutePath)
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            recorder.setVideoSize(width, height)
            recorder.setVideoFrameRate(fps)
            recorder.setVideoEncodingBitRate(bitrate)
            if (withAudio) {
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recorder.setAudioSamplingRate(44_100)
                recorder.setAudioEncodingBitRate(128_000)
            }
            recorder.setOrientationHint(rotation)
            recorder.prepare()
            val surface = recorder.surface
            recorder.start()
            return Session(file, surface, width, height, rotation, recorder)
        } catch (e: Exception) {
            recorder.release()
            file.delete()
            throw e
        }
    }

    /**
     * Finishes the file. Returns false (and deletes the file) if nothing usable was
     * recorded, e.g. the clip was too short to contain a frame.
     */
    fun stop(session: Session): Boolean = try {
        session.recorder.stop()
        true
    } catch (e: RuntimeException) {
        Log.w(TAG, "Recording too short or failed", e)
        session.file.delete()
        false
    } finally {
        session.recorder.release()
    }

    @Suppress("DEPRECATION")
    private fun newRecorder() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()

    private fun evenUp(v: Int) = if (v % 2 == 0) v else v + 1

    private companion object {
        const val TAG = "VideoRecorder"
    }
}
