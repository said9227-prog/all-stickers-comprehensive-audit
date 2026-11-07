package com.example.media

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import android.view.Surface
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Encodes a sequence of [Bitmap] frames into an MP4 (H.264) file using Android's
 * MediaCodec + MediaMuxer. Each bitmap is uploaded to a small EGL window surface
 * backed by the codec input surface and encoded on the fly.
 *
 * Usage:
 *     Mp4Exporter(file, width, height).use { exporter ->
 *         exporter.prepare(fps = 30)
 *         for (frame in frames) exporter.encodeFrame(frame, delayMs = 33)
 *         exporter.finish()
 *     }
 */
class Mp4Exporter(
    val outputFile: File,
    private val width: Int,
    private val height: Int
) : AutoCloseable {

    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var surface: Surface? = null
    private var eglDisplay: EGLDisplay? = null
    private var eglContext: EGLContext? = null
    private var eglSurface: EGLSurface? = null
    private var trackIndex = -1
    private var started = false
    private var presentationTimeUs = 0L

    private companion object {
        private const val MIME_TYPE = "video/avc"
        private const val BIT_RATE = 6_000_000
        private const val KEYFRAME_INTERVAL = 2
        private const val EGL_RECORDABLE_ANDROID = 0x3142
        private val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTextureCoord;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = aPosition;
                vTextureCoord = aTextureCoord;
            }
        """.trimIndent()
        private val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform sampler2D sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTextureCoord);
            }
        """.trimIndent()
        private val QUAD_VERTS = floatArrayOf(
            -1f, -1f, 0f, 0f, 1f,
            1f, -1f, 0f, 1f, 1f,
            -1f, 1f, 0f, 0f, 0f,
            1f, 1f, 0f, 1f, 0f
        )
    }

    fun prepare(fps: Int) {
        val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height)
        format.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        )
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, fps)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, KEYFRAME_INTERVAL)

        codec = MediaCodec.createEncoderByType(MIME_TYPE)
        codec!!.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        surface = codec!!.createInputSurface()
        codec!!.start()

        muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        initEgl(surface!!)
        started = true
    }

    private fun initEgl(inputSurface: Surface) {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val major = IntArray(1)
        val minor = IntArray(1)
        if (!EGL14.eglInitialize(eglDisplay!!, major, 0, minor, 0)) {
            throw IllegalStateException("eglInitialize failed")
        }

        val configAttribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 16,
            EGL14.EGL_STENCIL_SIZE, 0,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )
        val numConfigs = IntArray(1)
        val configs = arrayOfNulls<EGLConfig>(1)
        val chose = EGL14.eglChooseConfig(
            eglDisplay!!,
            configAttribs,
            0,
            configs,
            0,
            1,
            numConfigs,
            0
        )
        val config = configs[0]
        if (!chose || config == null) {
            throw IllegalStateException("No suitable EGL config")
        }

        val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(
            eglDisplay!!,
            config,
            EGL14.EGL_NO_CONTEXT,
            ctxAttribs,
            0
        )
        eglSurface = EGL14.eglCreateWindowSurface(
            eglDisplay!!,
            config,
            inputSurface,
            intArrayOf(EGL14.EGL_NONE),
            0
        )
        if (eglSurface == null || eglSurface == EGL14.EGL_NO_SURFACE) {
            throw IllegalStateException("eglCreateWindowSurface failed")
        }
        if (!EGL14.eglMakeCurrent(eglDisplay!!, eglSurface!!, eglSurface!!, eglContext!!)) {
            throw IllegalStateException("eglMakeCurrent failed")
        }
    }

    private var program: Int = 0
    private var aPosition: Int = -1
    private var aTexCoord: Int = -1
    private var uTexture: Int = -1
    private var vertexBuffer: FloatBuffer? = null

    private fun initGl() {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)

        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        aTexCoord = GLES20.glGetAttribLocation(program, "aTextureCoord")
        uTexture = GLES20.glGetUniformLocation(program, "sTexture")

        vertexBuffer = ByteBuffer.allocateDirect(QUAD_VERTS.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(QUAD_VERTS); position(0) }

        GLES20.glViewport(0, 0, width, height)
    }

    private fun loadShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val info = GLES20.glGetShaderInfoLog(shader)
            throw RuntimeException("Shader compile failed: $info")
        }
        return shader
    }

    /** Encodes one bitmap frame. [delayMs] is the delay since the previous frame. */
    fun encodeFrame(bitmap: Bitmap, delayMs: Long = 33) {
        if (!started) throw IllegalStateException("Mp4Exporter not prepared")
        if (program == 0) initGl()

        val texture = IntArray(1)
        GLES20.glGenTextures(1, texture, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0])
        GLES20.glTexParameterf(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR.toFloat()
        )
        GLES20.glTexParameterf(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR.toFloat()
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)

        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0])
        GLES20.glUniform1i(uTexture, 0)

        vertexBuffer!!.position(0)
        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, 20, vertexBuffer)
        vertexBuffer!!.position(3)
        GLES20.glEnableVertexAttribArray(aTexCoord)
        GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, 20, vertexBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glFinish()
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glDeleteTextures(1, texture, 0)

        EGL14.eglSwapBuffers(eglDisplay!!, eglSurface!!)
        presentationTimeUs += (delayMs.coerceAtLeast(1L) * 1000)
        drainEncoder(endOfStream = false)
    }

    private fun drainEncoder(endOfStream: Boolean) {
        if (endOfStream) codec!!.signalEndOfInputStream()
        val bufferInfo = MediaCodec.BufferInfo()
        var guard = 0
        while (true) {
            guard += 1
            if (guard > 2000) break
            val index = codec!!.dequeueOutputBuffer(
                bufferInfo,
                if (endOfStream) 10_000L else 0L
            )
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) break
                }
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val newFormat = codec!!.outputFormat
                    trackIndex = muxer!!.addTrack(newFormat)
                    muxer!!.start()
                }
                index >= 0 -> {
                    val encodedData = codec!!.getOutputBuffer(index)
                    if (encodedData != null) {
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            bufferInfo.size = 0
                        }
                        if (bufferInfo.size > 0 && trackIndex >= 0) {
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            bufferInfo.presentationTimeUs = presentationTimeUs
                            muxer!!.writeSampleData(trackIndex, encodedData, bufferInfo)
                        }
                    }
                    codec!!.releaseOutputBuffer(index, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
                else -> if (!endOfStream) break
            }
        }
    }

    fun finish() {
        if (!started) return
        started = false
        runCatching { drainEncoder(endOfStream = true) }
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        codec = null
        runCatching { muxer?.stop() }
        runCatching { muxer?.release() }
        muxer = null
        if (eglDisplay != null) {
            runCatching {
                EGL14.eglMakeCurrent(
                    eglDisplay!!,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT
                )
            }
            eglSurface?.let { runCatching { EGL14.eglDestroySurface(eglDisplay!!, it) } }
            eglContext?.let { runCatching { EGL14.eglDestroyContext(eglDisplay!!, it) } }
            runCatching { EGL14.eglReleaseThread() }
            runCatching { EGL14.eglTerminate(eglDisplay!!) }
        }
        eglSurface = null
        eglContext = null
        eglDisplay = null
        surface?.release()
        surface = null
    }

    override fun close() {
        finish()
    }
}
