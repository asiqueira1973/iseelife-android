// GlPreviewView.kt — GLSurfaceView do visor (épico 8.3). Cria o contexto GL ES 2, hospeda o
// CameraGlRenderer e expõe (a) um callback de quando a SurfaceTexture da câmera está pronta (para
// o CameraController ligar o CameraX) e (b) a atualização dos uniforms da receita ativa.

package com.dablab.iseelife.camera

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLSurfaceView
import com.dablab.iseelife.filtros.RecipeUniforms

class GlPreviewView(context: Context) : GLSurfaceView(context) {

    // Definido pela tela antes/depois de adicionar a view; chamado na main thread.
    var aoSurfaceTexturePronta: ((SurfaceTexture) -> Unit)? = null

    private val renderer = CameraGlRenderer(
        aoPreparar = { st -> post { aoSurfaceTexturePronta?.invoke(st) } },
        pedirRender = { requestRender() },
    )

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY   // só redesenha quando chega frame novo
    }

    fun definirUniforms(u: RecipeUniforms) = renderer.definirUniforms(u)
    fun definirTamanhoFrame(w: Int, h: Int) = renderer.definirTamanhoFrame(w, h)

    fun liberarGl() = queueEvent { renderer.liberar() }
}
