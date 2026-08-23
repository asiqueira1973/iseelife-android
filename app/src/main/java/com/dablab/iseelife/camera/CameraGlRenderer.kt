// CameraGlRenderer.kt — GLSurfaceView.Renderer do preview (épico 8.3). Recebe os frames da câmera
// numa textura externa (OES) via SurfaceTexture e os desenha através do RecipeGlPipeline (passe A
// aceita textura externa) na tela, em aspect-fill sobre fundo preto. É o lado "preview" do WYSIWYG
// (princípio 0.2): o mesmo pipeline da captura.
//
// NOTA DE DEVICE: aspect-fill + rotação dependem da matriz da SurfaceTexture e da resolução do
// frame — o ajuste fino é feito no aparelho (mesmo caminho do iOS). A versão aqui compõe a matriz
// da SurfaceTexture com um crop central para preencher a view.

package com.dablab.iseelife.camera

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.dablab.iseelife.filtros.RecipeGlPipeline
import com.dablab.iseelife.filtros.RecipeUniforms
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class CameraGlRenderer(
    // Chamado (na thread GL) quando a SurfaceTexture da câmera está pronta para ser ligada ao
    // CameraX. Também informa qual GLSurfaceView deve pedir novo frame.
    private val aoPreparar: (SurfaceTexture) -> Unit,
    private val pedirRender: () -> Unit,
) : GLSurfaceView.Renderer {

    private var oesTex = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var pipeline: RecipeGlPipeline? = null

    private val texMatrix = FloatArray(16)
    private val cropMatrix = FloatArray(16)
    private val combinado = FloatArray(16)

    private var viewW = 1
    private var viewH = 1
    @Volatile private var frameW = 0
    @Volatile private var frameH = 0
    private var seed = 0f

    // Receita ativa (com forceMono já embutido). Atualizada pela UI; lida na thread GL.
    @Volatile private var uniforms: RecipeUniforms? = null

    fun definirUniforms(u: RecipeUniforms) { uniforms = u }

    // Resolução do frame que a câmera vai entregar (vinda do SurfaceRequest do CameraX).
    fun definirTamanhoFrame(w: Int, h: Int) { frameW = w; frameH = h }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)

        // Textura externa (OES) que recebe os frames da câmera.
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        oesTex = tex[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        val st = SurfaceTexture(oesTex)
        st.setOnFrameAvailableListener { pedirRender() }
        surfaceTexture = st

        pipeline = RecipeGlPipeline(externalInput = true)

        aoPreparar(st)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewW = width.coerceAtLeast(1)
        viewH = height.coerceAtLeast(1)
    }

    override fun onDrawFrame(gl: GL10?) {
        val st = surfaceTexture ?: return
        val pipe = pipeline ?: return
        val u = uniforms ?: return

        st.updateTexImage()
        st.getTransformMatrix(texMatrix)

        // Aspect-fill: crop central compondo a matriz da SurfaceTexture com um scale ao redor do
        // centro (0.5,0.5). A resolução efetiva considera a rotação (retrato x paisagem).
        val ew = if (frameW > 0) frameW else viewW
        val eh = if (frameH > 0) frameH else viewH
        montarCrop(viewW, viewH, ew, eh)
        Matrix.multiplyMM(combinado, 0, texMatrix, 0, cropMatrix, 0)

        seed += 1f
        // FBO intermediário no tamanho da view; passe B desenha na tela (fbo 0).
        pipe.render(
            inputTex = oesTex,
            texMatrix = combinado,
            larguraPx = viewW, alturaPx = viewH,
            u = u, seed = seed,
            outFbo = 0, outW = viewW, outH = viewH,
        )
    }

    // Monta em `cropMatrix` um scale central que preenche a view sem distorcer (aspect-fill).
    private fun montarCrop(vw: Int, vh: Int, fw: Int, fh: Int) {
        val viewAspect = vw.toFloat() / vh
        val frameAspect = fw.toFloat() / fh
        var sx = 1f
        var sy = 1f
        if (frameAspect > viewAspect) sx = viewAspect / frameAspect else sy = frameAspect / viewAspect
        Matrix.setIdentityM(cropMatrix, 0)
        Matrix.translateM(cropMatrix, 0, 0.5f, 0.5f, 0f)
        Matrix.scaleM(cropMatrix, 0, sx, sy, 1f)
        Matrix.translateM(cropMatrix, 0, -0.5f, -0.5f, 0f)
    }

    fun liberar() {
        pipeline?.liberar(); pipeline = null
        surfaceTexture?.release(); surfaceTexture = null
        if (oesTex != 0) { GLES20.glDeleteTextures(1, intArrayOf(oesTex), 0); oesTex = 0 }
    }
}
