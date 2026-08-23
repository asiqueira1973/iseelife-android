// OffscreenRecipeRenderer.kt — aplica o pipeline de receita a um Bitmap fora da tela (épico 8.2),
// via um contexto EGL PBuffer + framebuffer object. É o caminho da CAPTURA (épico 8.6): o mesmo
// shader do preview roda sobre o frame cru antes de codificar o JPEG (WYSIWYG, princípio 0.2).
// Também serve para validar o pipeline sem câmera (render de uma imagem de teste).
//
// Uso: criar UMA instância (monta o contexto GL), chamar `aplicar(...)` quantas vezes precisar,
// e `liberar()` ao final. Não é thread-safe: use sempre na mesma thread.

package com.dablab.iseelife.filtros

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder

class OffscreenRecipeRenderer {

    private val display: EGLDisplay
    private val context: EGLContext
    private val surface: EGLSurface
    private val pipeline: RecipeGlPipeline

    init {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        require(display != EGL14.EGL_NO_DISPLAY) { "EGL: sem display" }
        val versao = IntArray(2)
        require(EGL14.eglInitialize(display, versao, 0, versao, 1)) { "EGL: falha ao inicializar" }

        // Config PBuffer, RGBA8, OpenGL ES 2.0.
        val atributos = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfig = IntArray(1)
        require(EGL14.eglChooseConfig(display, atributos, 0, configs, 0, 1, numConfig, 0) && numConfig[0] > 0) {
            "EGL: nenhuma config compatível"
        }
        val config = configs[0]!!

        context = EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
        )
        require(context != EGL14.EGL_NO_CONTEXT) { "EGL: falha ao criar contexto" }

        // Superfície PBuffer mínima (1x1); o render de fato vai para um FBO dimensionado por imagem.
        surface = EGL14.eglCreatePbufferSurface(
            display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0,
        )
        require(surface != EGL14.EGL_NO_SURFACE) { "EGL: falha ao criar surface" }

        require(EGL14.eglMakeCurrent(display, surface, surface, context)) { "EGL: makeCurrent falhou" }

        // O pipeline (dois passes, entrada 2D) só pode ser criado com o contexto corrente.
        pipeline = RecipeGlPipeline(externalInput = false)
    }

    // Aplica a receita ao `entrada` e devolve um novo Bitmap filtrado (mesmas dimensões).
    // forceMonochrome = preview P&B (só afeta este resultado, não persiste como regra).
    fun aplicar(
        entrada: Bitmap,
        receita: com.dablab.iseelife.dados.Receita,
        forceMonochrome: Boolean = false,
        seed: Float = 0f,
    ): Bitmap {
        val w = entrada.width
        val h = entrada.height
        val uniforms = RecipeUniforms.de(receita, forceMonochrome)

        // Textura de entrada a partir do bitmap.
        val texIn = IntArray(1)
        GLES20.glGenTextures(1, texIn, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texIn[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, entrada, 0)

        // Textura de saída + FBO.
        val texOut = IntArray(1)
        GLES20.glGenTextures(1, texOut, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texOut[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null,
        )
        val fbo = IntArray(1)
        GLES20.glGenFramebuffers(1, fbo, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0])
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, texOut[0], 0,
        )
        require(GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE) {
            "FBO incompleto"
        }

        // Render em dois passes (cor → FBO intermediário → espacial/grão → este FBO de saída).
        pipeline.render(
            inputTex = texIn[0],
            texMatrix = RecipeGlPipeline.IDENTIDADE,
            larguraPx = w, alturaPx = h,
            u = uniforms, seed = seed,
            outFbo = fbo[0], outW = w, outH = h,
        )

        // Lê os pixels de volta para um Bitmap.
        val buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
        GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
        val saida = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        buf.rewind()
        saida.copyPixelsFromBuffer(buf)

        // Limpeza dos recursos desta chamada.
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glDeleteFramebuffers(1, fbo, 0)
        GLES20.glDeleteTextures(1, texIn, 0)
        GLES20.glDeleteTextures(1, texOut, 0)
        return saida
    }

    // Destrói o pipeline e o contexto EGL. Chamar quando não for mais capturar.
    fun liberar() {
        pipeline.liberar()
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(display, surface)
        EGL14.eglDestroyContext(display, context)
        EGL14.eglTerminate(display)
    }
}
