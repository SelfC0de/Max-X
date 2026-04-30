package ru.maxx.app.core.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class VoiceRecorder(private val ctx: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _amplitude = MutableStateFlow(0)
    val amplitude: StateFlow<Int> = _amplitude

    fun start(): File? {
        stopAndRelease()
        val file = File(ctx.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        outputFile = file
        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(ctx)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(128000)
            setOutputFile(file.absolutePath)
            runCatching { prepare(); start() }
        }
        _isRecording.value = true
        return file
    }

    fun amplitude(): Int = recorder?.maxAmplitude ?: 0

    fun stop(): File? {
        if (!_isRecording.value) return null
        runCatching { recorder?.stop() }
        stopAndRelease()
        _isRecording.value = false
        return outputFile
    }

    fun cancel() {
        stopAndRelease()
        outputFile?.delete()
        outputFile = null
        _isRecording.value = false
    }

    private fun stopAndRelease() {
        runCatching { recorder?.release() }
        recorder = null
    }
}
