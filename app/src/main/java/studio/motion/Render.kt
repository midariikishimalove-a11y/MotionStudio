package studio.motion

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.opengl.GLES20
import androidx.media3.common.util.Size
import androidx.media3.common.*
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.*
import androidx.media3.transformer.*
import java.io.File

/** Constrói uma Composition a partir do Project. A MESMA composition alimenta a pré-visualização e a exportação. */
@UnstableApi
object Render {
    private fun lut(ctx: Context, uri: String): SingleColorLut? = runCatching {
        val lines = ctx.contentResolver.openInputStream(Uri.parse(uri))!!.bufferedReader().readLines()
        val n = lines.first { it.startsWith("LUT_3D_SIZE") }.trim().split(Regex("\\s+"))[1].toInt()
        val cube = Array(n) { Array(n) { IntArray(n) } }
        var i = 0
        for (l in lines) {
            val v = l.trim().split(Regex("\\s+"))
            if (v.size != 3 || v[0].firstOrNull()?.let { it.isDigit() || it == '.' } != true) continue
            val r = i % n; val g = (i / n) % n; val b = i / (n * n); i++
            cube[r][g][b] = Color.argb(255, (v[0].toFloat() * 255).toInt(), (v[1].toFloat() * 255).toInt(), (v[2].toFloat() * 255).toInt())
        }
        SingleColorLut.createFromCube(cube)
    }.getOrNull()

    private fun edited(ctx: Context, c: Clip): EditedMediaItem {
        val item = MediaItem.Builder().setUri(c.uri).setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder().setStartPositionMs(c.inMs).setEndPositionMs(c.outMs).build()
        ).build()
        val v = mutableListOf<Effect>()
        if (c.speed != 1f) v += SpeedChangeEffect(c.speed)
        if (c.brightness != 0f) v += Brightness(c.brightness)
        if (c.contrast != 0f) v += Contrast(c.contrast)
        if (c.saturation != 0f) v += HslAdjustment.Builder().adjustSaturation(c.saturation).build()
        if (c.grayscale) v += RgbFilter.createGrayscaleFilter()
        c.lutUri?.let { u -> lut(ctx, u)?.let { v += it } }
        c.chromaKey?.let { v += ChromaKeyEffect(it) }
        val a = mutableListOf<AudioProcessor>()
        if (c.speed != 1f) a += SonicAudioProcessor().apply { setSpeed(c.speed); setPitch(1f) }
        if (c.volume != 1f) a += ChannelMixingAudioProcessor().apply {
            for (ch in 1..2) putChannelMixingMatrix(ChannelMixingMatrix.create(ch, ch).scaleBy(c.volume))
        }
        return EditedMediaItem.Builder(item).setEffects(Effects(a, v)).build()
    }

    fun composition(ctx: Context, p: Project, height: Int? = null, fps: Int? = null): Composition {
        val seqs = p.tracks.filter { it.isNotEmpty() }.map { t -> EditedMediaItemSequence(t.map { edited(ctx, it) }) }
        val fx = mutableListOf<Effect>(Presentation.createForAspectRatio(p.aspect, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP))
        if (fps != null) fx += FrameDropEffect.createDefaultFrameDropEffect(fps.toFloat())
        if (height != null) fx += Presentation.createForHeight(height)
        return Composition.Builder(seqs).setEffects(Effects(emptyList(), fx)).build()
    }

    /** Exportação real via MediaCodec (aceleração por hardware). Deve ser chamada na thread principal. */
    fun export(ctx: Context, p: Project, height: Int, fps: Int, bitrate: Int?, hevc: Boolean, out: File,
               done: (Result<File>) -> Unit): Transformer {
        val enc = DefaultEncoderFactory.Builder(ctx).apply {
            if (bitrate != null) setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(bitrate).build())
        }.build()
        val t = Transformer.Builder(ctx)
            .setVideoMimeType(if (hevc) MimeTypes.VIDEO_H265 else MimeTypes.VIDEO_H264)
            .setEncoderFactory(enc)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(c: Composition, r: ExportResult) = done(Result.success(out))
                override fun onError(c: Composition, r: ExportResult, e: ExportException) = done(Result.failure(e))
            }).build()
        t.start(composition(ctx, p, height, fps), out.absolutePath)
        return t
    }
}

/** Chroma key (tela verde) como GlEffect próprio: pixels próximos de [key] ficam transparentes. */
@UnstableApi
class ChromaKeyEffect(private val key: Int, private val tolerance: Float = 0.35f) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = Program(key, tolerance, useHdr)

    private class Program(key: Int, tol: Float, useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
        private val gl = GlProgram(VERT, FRAG).also {
            it.setFloatsUniform("uKey", floatArrayOf(Color.red(key) / 255f, Color.green(key) / 255f, Color.blue(key) / 255f))
            it.setFloatUniform("uTol", tol)
        }
        override fun configure(inputWidth: Int, inputHeight: Int) = Size(inputWidth, inputHeight)
        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                gl.use()
                gl.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                gl.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
                gl.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
        }
        override fun release() {
            super.release()
            try { gl.delete() } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
        }
    }
    private companion object {
        const val VERT = "attribute vec4 aFramePosition; varying vec2 vTex;\n" +
            "void main(){ gl_Position = aFramePosition; vTex = aFramePosition.xy * 0.5 + 0.5; }"
        const val FRAG = "precision mediump float; uniform sampler2D uTexSampler; uniform vec3 uKey; uniform float uTol; varying vec2 vTex;\n" +
            "void main(){ vec4 c = texture2D(uTexSampler, vTex); float d = distance(c.rgb, uKey);\n" +
            "float a = smoothstep(uTol, uTol + 0.12, d); gl_FragColor = vec4(c.rgb * a, a); }"
    }
}
