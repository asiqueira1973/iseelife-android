// RecipeGlPipeline.kt — pipeline de filtro em DOIS PASSES (épico 8.2, regra 11.6). Orquestra:
//   Passe A (cor): entrada (externa da câmera OU 2D do bitmap) → FBO intermediário.
//   Passe B (espacial + grão): FBO intermediário → alvo de saída (tela no preview, ou FBO de
//     captura no offscreen), amostrando a COR JÁ PROCESSADA (corrige o bug de amostrar a entrada
//     crua na clareza/nitidez).
// Reutilizado por preview (épico 8.3) e captura (OffscreenRecipeRenderer). Usar sempre na thread
// que tem o contexto GL corrente.

package com.dablab.iseelife.filtros

import android.opengl.GLES11Ext
import android.opengl.GLES20
import java.nio.FloatBuffer

class RecipeGlPipeline(externalInput: Boolean) {

    private val quad: FloatBuffer = GlUtil.criarBufferQuad()
    private val alvoEntrada = if (externalInput) GLES11Ext.GL_TEXTURE_EXTERNAL_OES else GLES20.GL_TEXTURE_2D

    // Passe A (cor)
    private val progColor = GlUtil.criarPrograma(ShaderSources.VERTEX, ShaderSources.fragmentColor(externalInput))
    private val aPosColor = GLES20.glGetAttribLocation(progColor, "aPosition")
    private val aTexColor = GLES20.glGetAttribLocation(progColor, "aTexCoord")
    private val uTexMatrixC = GLES20.glGetUniformLocation(progColor, "uTexMatrix")
    private val uTextureC = GLES20.glGetUniformLocation(progColor, "uTexture")
    private val uNeutra = GLES20.glGetUniformLocation(progColor, "uNeutra")
    private val uWbGain = GLES20.glGetUniformLocation(progColor, "uWbGain")
    private val uRbGain = GLES20.glGetUniformLocation(progColor, "uRbGain")
    private val uShadowShift = GLES20.glGetUniformLocation(progColor, "uShadowShift")
    private val uHighlightShift = GLES20.glGetUniformLocation(progColor, "uHighlightShift")
    private val uMono = GLES20.glGetUniformLocation(progColor, "uMono")
    private val uMonoWeights = GLES20.glGetUniformLocation(progColor, "uMonoWeights")
    private val uSaturation = GLES20.glGetUniformLocation(progColor, "uSaturation")
    private val uChrome = GLES20.glGetUniformLocation(progColor, "uChrome")

    // Passe B (espacial + grão)
    private val progSpatial = GlUtil.criarPrograma(ShaderSources.VERTEX, ShaderSources.fragmentSpatial())
    private val aPosSpatial = GLES20.glGetAttribLocation(progSpatial, "aPosition")
    private val aTexSpatial = GLES20.glGetAttribLocation(progSpatial, "aTexCoord")
    private val uTexMatrixS = GLES20.glGetUniformLocation(progSpatial, "uTexMatrix")
    private val uSource = GLES20.glGetUniformLocation(progSpatial, "uSource")
    private val uTexSize = GLES20.glGetUniformLocation(progSpatial, "uTexSize")
    private val uClarity = GLES20.glGetUniformLocation(progSpatial, "uClarity")
    private val uSharpness = GLES20.glGetUniformLocation(progSpatial, "uSharpness")
    private val uGrainOpacity = GLES20.glGetUniformLocation(progSpatial, "uGrainOpacity")
    private val uGrainScale = GLES20.glGetUniformLocation(progSpatial, "uGrainScale")
    private val uSeed = GLES20.glGetUniformLocation(progSpatial, "uSeed")

    // FBO intermediário (saída do passe A), recriado quando o tamanho muda.
    private var interTex = 0
    private var interFbo = 0
    private var interW = 0
    private var interH = 0

    // Renderiza a entrada aplicando a receita e escreve no alvo `outFbo` (0 = framebuffer padrão/
    // tela). `larguraPx/alturaPx` = resolução da imagem/frame (define o FBO intermediário e a
    // amostragem de vizinhança). `outW/outH` = viewport do alvo. `texMatrix` transforma as coords
    // da entrada (matriz da SurfaceTexture no preview; identidade no bitmap).
    fun render(
        inputTex: Int,
        texMatrix: FloatArray,
        larguraPx: Int,
        alturaPx: Int,
        u: RecipeUniforms,
        seed: Float,
        outFbo: Int,
        outW: Int,
        outH: Int,
    ) {
        garantirIntermediario(larguraPx, alturaPx)

        // ---- Passe A: cor → FBO intermediário ----
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, interFbo)
        GLES20.glViewport(0, 0, larguraPx, alturaPx)
        GLES20.glUseProgram(progColor)
        ligarGeometria(aPosColor, aTexColor)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(alvoEntrada, inputTex)
        GLES20.glUniform1i(uTextureC, 0)
        GLES20.glUniformMatrix4fv(uTexMatrixC, 1, false, texMatrix, 0)
        GLES20.glUniform1i(uNeutra, if (u.neutra) 1 else 0)
        GLES20.glUniform3fv(uWbGain, 1, u.wbGain, 0)
        GLES20.glUniform3fv(uRbGain, 1, u.rbGain, 0)
        GLES20.glUniform1f(uShadowShift, u.shadowShift)
        GLES20.glUniform1f(uHighlightShift, u.highlightShift)
        GLES20.glUniform1i(uMono, if (u.mono) 1 else 0)
        GLES20.glUniform3fv(uMonoWeights, 1, u.monoWeights, 0)
        GLES20.glUniform1f(uSaturation, u.saturation)
        GLES20.glUniform1f(uChrome, u.chrome)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        desligarGeometria(aPosColor, aTexColor)

        // ---- Passe B: espacial + grão → alvo de saída ----
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, outFbo)
        GLES20.glViewport(0, 0, outW, outH)
        GLES20.glUseProgram(progSpatial)
        ligarGeometria(aPosSpatial, aTexSpatial)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, interTex)
        GLES20.glUniform1i(uSource, 0)
        GLES20.glUniformMatrix4fv(uTexMatrixS, 1, false, IDENTIDADE, 0)
        GLES20.glUniform2f(uTexSize, larguraPx.toFloat(), alturaPx.toFloat())
        GLES20.glUniform1f(uClarity, u.clarity)
        GLES20.glUniform1f(uSharpness, u.sharpness)
        GLES20.glUniform1f(uGrainOpacity, u.grainOpacity)
        GLES20.glUniform1f(uGrainScale, u.grainScale)
        GLES20.glUniform1f(uSeed, seed)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        desligarGeometria(aPosSpatial, aTexSpatial)
    }

    // Liga posição + coords de textura do quad (stride 4 floats: x,y,u,v).
    private fun ligarGeometria(aPos: Int, aTex: Int) {
        quad.position(0)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 16, quad)
        quad.position(2)
        GLES20.glEnableVertexAttribArray(aTex)
        GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 16, quad)
    }

    private fun desligarGeometria(aPos: Int, aTex: Int) {
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aTex)
    }

    // (Re)cria o FBO intermediário no tamanho da imagem.
    private fun garantirIntermediario(w: Int, h: Int) {
        if (interFbo != 0 && interW == w && interH == h) return
        liberarIntermediario()
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        val fbo = IntArray(1)
        GLES20.glGenFramebuffers(1, fbo, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0])
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, tex[0], 0)
        require(GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE) {
            "FBO intermediário incompleto"
        }
        interTex = tex[0]; interFbo = fbo[0]; interW = w; interH = h
    }

    private fun liberarIntermediario() {
        if (interFbo != 0) { GLES20.glDeleteFramebuffers(1, intArrayOf(interFbo), 0); interFbo = 0 }
        if (interTex != 0) { GLES20.glDeleteTextures(1, intArrayOf(interTex), 0); interTex = 0 }
        interW = 0; interH = 0
    }

    fun liberar() {
        liberarIntermediario()
        GLES20.glDeleteProgram(progColor)
        GLES20.glDeleteProgram(progSpatial)
    }

    companion object {
        val IDENTIDADE = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f,
        )
    }
}
