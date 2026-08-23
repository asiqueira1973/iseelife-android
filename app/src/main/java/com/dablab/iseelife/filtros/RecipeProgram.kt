// RecipeProgram.kt — encapsula o programa GL do pipeline de receita (épico 8.2). Guarda as
// localizações de atributos/uniforms, envia os RecipeUniforms e desenha o quad a partir de uma
// textura de entrada. Reutilizado no preview (textura externa OES) e na captura (textura 2D),
// escolhendo o sabor do fragment shader por `external`.
//
// Deve ser criado e usado SEMPRE na thread que tem o contexto GL corrente.

package com.dablab.iseelife.filtros

import android.opengl.GLES11Ext
import android.opengl.GLES20
import java.nio.FloatBuffer

class RecipeProgram(external: Boolean) {

    private val programa = GlUtil.criarPrograma(ShaderSources.VERTEX, ShaderSources.fragment(external))
    private val alvoTextura = if (external) GLES11Ext.GL_TEXTURE_EXTERNAL_OES else GLES20.GL_TEXTURE_2D
    private val quad: FloatBuffer = GlUtil.criarBufferQuad()

    // Atributos
    private val aPosition = GLES20.glGetAttribLocation(programa, "aPosition")
    private val aTexCoord = GLES20.glGetAttribLocation(programa, "aTexCoord")
    // Uniforms
    private val uTexMatrix = GLES20.glGetUniformLocation(programa, "uTexMatrix")
    private val uTexture = GLES20.glGetUniformLocation(programa, "uTexture")
    private val uTexSize = GLES20.glGetUniformLocation(programa, "uTexSize")
    private val uNeutra = GLES20.glGetUniformLocation(programa, "uNeutra")
    private val uWbGain = GLES20.glGetUniformLocation(programa, "uWbGain")
    private val uRbGain = GLES20.glGetUniformLocation(programa, "uRbGain")
    private val uShadowShift = GLES20.glGetUniformLocation(programa, "uShadowShift")
    private val uHighlightShift = GLES20.glGetUniformLocation(programa, "uHighlightShift")
    private val uMono = GLES20.glGetUniformLocation(programa, "uMono")
    private val uMonoWeights = GLES20.glGetUniformLocation(programa, "uMonoWeights")
    private val uSaturation = GLES20.glGetUniformLocation(programa, "uSaturation")
    private val uChrome = GLES20.glGetUniformLocation(programa, "uChrome")
    private val uClarity = GLES20.glGetUniformLocation(programa, "uClarity")
    private val uSharpness = GLES20.glGetUniformLocation(programa, "uSharpness")
    private val uGrainOpacity = GLES20.glGetUniformLocation(programa, "uGrainOpacity")
    private val uGrainScale = GLES20.glGetUniformLocation(programa, "uGrainScale")
    private val uSeed = GLES20.glGetUniformLocation(programa, "uSeed")

    // Desenha a `textura` de entrada aplicando a receita. `texMatrix` transforma as coords (4x4;
    // identidade no caminho 2D). `larguraPx/alturaPx` alimentam a amostragem de vizinhança.
    // `seed` desloca o grão (use valores diferentes por frame para o grão não "grudar").
    fun desenhar(
        textura: Int,
        u: RecipeUniforms,
        texMatrix: FloatArray,
        larguraPx: Int,
        alturaPx: Int,
        seed: Float,
    ) {
        GLES20.glUseProgram(programa)

        // Geometria (posição + coords de textura no mesmo buffer intercalado: stride 4 floats).
        quad.position(0)
        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 16, quad)
        quad.position(2)
        GLES20.glEnableVertexAttribArray(aTexCoord)
        GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, 16, quad)

        // Textura de entrada na unidade 0.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(alvoTextura, textura)
        GLES20.glUniform1i(uTexture, 0)

        GLES20.glUniformMatrix4fv(uTexMatrix, 1, false, texMatrix, 0)
        GLES20.glUniform2f(uTexSize, larguraPx.toFloat(), alturaPx.toFloat())

        // Parâmetros da receita.
        GLES20.glUniform1i(uNeutra, if (u.neutra) 1 else 0)
        GLES20.glUniform3fv(uWbGain, 1, u.wbGain, 0)
        GLES20.glUniform3fv(uRbGain, 1, u.rbGain, 0)
        GLES20.glUniform1f(uShadowShift, u.shadowShift)
        GLES20.glUniform1f(uHighlightShift, u.highlightShift)
        GLES20.glUniform1i(uMono, if (u.mono) 1 else 0)
        GLES20.glUniform3fv(uMonoWeights, 1, u.monoWeights, 0)
        GLES20.glUniform1f(uSaturation, u.saturation)
        GLES20.glUniform1f(uChrome, u.chrome)
        GLES20.glUniform1f(uClarity, u.clarity)
        GLES20.glUniform1f(uSharpness, u.sharpness)
        GLES20.glUniform1f(uGrainOpacity, u.grainOpacity)
        GLES20.glUniform1f(uGrainScale, u.grainScale)
        GLES20.glUniform1f(uSeed, seed)

        GlUtil.checar("uniforms")
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPosition)
        GLES20.glDisableVertexAttribArray(aTexCoord)
    }

    fun liberar() { GLES20.glDeleteProgram(programa) }

    companion object {
        // Matriz identidade 4x4 (caminho 2D/offscreen — sem transformação de coords).
        val IDENTIDADE = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f,
        )
    }
}
