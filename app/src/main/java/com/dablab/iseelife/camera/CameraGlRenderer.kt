// CameraGlRenderer.kt — GLSurfaceView.Renderer do preview (épico 8.3). Recebe os frames da câmera
// numa textura externa (OES) via SurfaceTexture e os desenha através do RecipeGlPipeline (passe A
// aceita textura externa) na tela, em aspect-fill sobre fundo preto. É o lado "preview" do WYSIWYG
// (princípio 0.2): o mesmo pipeline da captura.
//
// NOTA DE DEVICE: aspect-fill + rotação dependem da matriz da SurfaceTexture e da resolução do
// frame — o ajuste fino é feito no aparelho (mesmo caminho do iOS). A versão aqui compõe a matriz
// da SurfaceTexture com um crop central para preencher a view.

package com.dablab.iseelife.camera

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import com.dablab.iseelife.filtros.RecipeGlPipeline
import com.dablab.iseelife.filtros.RecipeUniforms
import java.nio.ByteBuffer
import java.nio.ByteOrder
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

    // Captura (épico 8.6): quando setado, o próximo frame é renderizado em full-res para um FBO e
    // lido de volta como Bitmap (o MESMO shader do preview → WYSIWYG). Callback na main thread.
    @Volatile private var aoCapturar: ((Bitmap) -> Unit)? = null
    private var capFbo = 0
    private var capTex = 0
    private var capW = 0
    private var capH = 0

    fun definirUniforms(u: RecipeUniforms) { uniforms = u }

    // Solicita a captura do próximo frame filtrado (chama de qualquer thread).
    fun solicitarCaptura(callback: (Bitmap) -> Unit) {
        aoCapturar = callback
        pedirRender()
    }

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

        // Captura solicitada: renderiza EXATAMENTE o que está no preview (mesma matriz `combinado`
        // com orientação + aspect-fill), no tamanho da view → foto em retrato, WYSIWYG. O teto de
        // detalhe é a resolução do frame da câmera; capturar no tamanho da view preserva o
        // enquadramento visto. (Captura em maior resolução é otimização de roadmap.)
        val cb = aoCapturar
        if (cb != null) {
            aoCapturar = null
            garantirCaptura(viewW, viewH)
            pipe.render(
                inputTex = oesTex,
                texMatrix = combinado,
                larguraPx = viewW, alturaPx = viewH,
                u = u, seed = seed,
                outFbo = capFbo, outW = viewW, outH = viewH,
            )
            val bmp = lerBitmap(capFbo, viewW, viewH)
            Handler(Looper.getMainLooper()).post { cb(bmp) }
        }
    }

    // (Re)cria o FBO de captura no tamanho do frame.
    private fun garantirCaptura(w: Int, h: Int) {
        if (capFbo != 0 && capW == w && capH == h) return
        if (capFbo != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(capFbo), 0)
        if (capTex != 0) GLES20.glDeleteTextures(1, intArrayOf(capTex), 0)
        val tex = IntArray(1); GLES20.glGenTextures(1, tex, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        val fbo = IntArray(1); GLES20.glGenFramebuffers(1, fbo, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0])
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, tex[0], 0)
        capTex = tex[0]; capFbo = fbo[0]; capW = w; capH = h
    }

    // Lê os pixels de um FBO para um Bitmap ARGB_8888.
    private fun lerBitmap(fbo: Int, w: Int, h: Int): Bitmap {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        val buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
        GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        buf.rewind(); bmp.copyPixelsFromBuffer(buf)
        return bmp
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
        if (capFbo != 0) { GLES20.glDeleteFramebuffers(1, intArrayOf(capFbo), 0); capFbo = 0 }
        if (capTex != 0) { GLES20.glDeleteTextures(1, intArrayOf(capTex), 0); capTex = 0 }
    }
}
