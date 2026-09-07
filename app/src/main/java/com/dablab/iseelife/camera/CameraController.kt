// CameraController.kt — liga o CameraX ao preview GL e concentra os controles de câmera (épicos
// 8.3/8.4). Vincula dois use cases: Preview (frames → SurfaceTexture → shader) e ImageAnalysis
// (luminância → loop de ISO da prioridade de obturador). Expõe foco por toque (Modo Rua) e a
// ativação/desativação da prioridade de obturador. O binding respeita o ciclo de vida.

package com.dablab.iseelife.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.dablab.iseelife.dados.Receita
import com.dablab.iseelife.filtros.OffscreenRecipeRenderer
import java.util.concurrent.Executors

class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
) {
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private val analiseExecutor = Executors.newSingleThreadExecutor()
    // Captura em ALTA resolução: use case ImageCapture (foto do sensor, ~12MP) + thread única para
    // aplicar a receita off-screen (o OffscreenRecipeRenderer tem contexto EGL preso à thread).
    private var imageCapture: ImageCapture? = null
    private val capturaExecutor = Executors.newSingleThreadExecutor()
    private val exposicao = ExposureController()
    private val manual = ManualControls()

    var lentes: LensDiscovery? = null
        private set

    // Faixas dos sliders do Modo Pro (disponíveis após o bind). Null antes de ligar.
    var faixasManuais: FaixasManuais? = null
        private set

    fun inicializar(onReady: (LensDiscovery) -> Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val p = future.get()
            provider = p
            val ld = LensDiscovery(p)
            lentes = ld
            onReady(ld)
        }, ContextCompat.getMainExecutor(context))
    }

    // Vincula Preview + ImageAnalysis à lente escolhida. `aoTamanhoFrame` informa a resolução real
    // do frame para o aspect-fill do preview.
    fun ligar(
        surfaceTexture: SurfaceTexture,
        seletor: CameraSelector,
        aoTamanhoFrame: (Int, Int) -> Unit,
    ) {
        val p = provider ?: return
        val exec = ContextCompat.getMainExecutor(context)

        val preview = Preview.Builder().build()
        preview.setSurfaceProvider(exec) { request ->
            val res = request.resolution
            surfaceTexture.setDefaultBufferSize(res.width, res.height)
            aoTamanhoFrame(res.width, res.height)
            val surface = Surface(surfaceTexture)
            request.provideSurface(surface, exec) { surface.release() }
        }

        // ImageAnalysis: luminância média → loop de ISO da prioridade de obturador.
        val analise = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analise.setAnalyzer(analiseExecutor, LumaAnalyzer { luma -> exposicao.aoLuma(luma) })

        // ImageCapture: foto do sensor em resolução máxima (conserto do ~2MP). Latência mínima para
        // não perder o momento na rua. Sem som forçado no Android (mantém o app silencioso no Brasil).
        val captura = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        p.unbindAll()
        // Preview + ImageAnalysis + ImageCapture é combinação suportada; se algum device recusar os
        // três, cai para dois (sem alta-res) e a captura usa o fallback do frame do preview.
        camera = try {
            val c = p.bindToLifecycle(lifecycleOwner, seletor, preview, analise, captura)
            imageCapture = captura
            c
        } catch (e: Exception) {
            imageCapture = null
            p.bindToLifecycle(lifecycleOwner, seletor, preview, analise)
        }
        camera?.let {
            exposicao.configurar(it)
            faixasManuais = manual.configurar(it)
        }
    }

    // Captura em ALTA resolução (foto do sensor) e aplica a receita em resolução cheia via o mesmo
    // shader do preview (WYSIWYG). Devolve o Bitmap filtrado na main thread — ou `null` se não houver
    // ImageCapture disponível ou der erro, para o chamador cair no fallback (frame do preview).
    fun capturarAltaResolucao(receita: Receita, forceMono: Boolean, onResult: (Bitmap?) -> Unit) {
        val captura = imageCapture ?: run { onResult(null); return }
        val main = ContextCompat.getMainExecutor(context)
        captura.takePicture(capturaExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val bruto = image.toBitmap()
                    val rot = image.imageInfo.rotationDegrees
                    image.close()
                    // Roda o bitmap para retrato (upright) conforme a rotação do sensor.
                    val emPe = if (rot != 0) {
                        val m = Matrix().apply { postRotate(rot.toFloat()) }
                        Bitmap.createBitmap(bruto, 0, 0, bruto.width, bruto.height, m, true)
                            .also { if (it != bruto) bruto.recycle() }
                    } else bruto
                    // Aplica a receita em resolução cheia (mesmo shader do preview → WYSIWYG).
                    val renderer = OffscreenRecipeRenderer()
                    val filtrada = try {
                        renderer.aplicar(emPe, receita, forceMono)
                    } finally {
                        renderer.liberar()
                    }
                    if (filtrada != emPe) emPe.recycle()
                    main.execute { onResult(filtrada) }
                } catch (e: Throwable) {
                    try { image.close() } catch (_: Throwable) {}
                    main.execute { onResult(null) }
                }
            }

            override fun onError(exc: ImageCaptureException) {
                main.execute { onResult(null) }
            }
        })
    }

    // Controles manuais do Modo Pro (épico 8.5) — passthrough para ManualControls.
    fun proISO(v: Int) = manual.definirISO(v)
    fun proObturador(ns: Long) = manual.definirObturador(ns)
    fun proEV(v: Float) = manual.definirEV(v)
    fun proFoco(pos: Float) = manual.definirFoco(pos)
    fun proWhiteBalance(temperatura: Int, tinta: Int) = manual.definirWhiteBalance(temperatura, tinta)
    fun proAuto() = manual.restaurarAutomatico()

    // Toque para travar foco no ponto (Modo Rua — épico 8.4). `x,y` em px na view (0..w, 0..h).
    // Sob prioridade de obturador, NÃO mede exposição (o loop de ISO governa) — só foco.
    fun focarNoPonto(x: Float, y: Float, larguraView: Int, alturaView: Int) {
        val cam = camera ?: return
        val factory = SurfaceOrientedMeteringPointFactory(larguraView.toFloat(), alturaView.toFloat())
        val ponto = factory.createPoint(x, y)
        val flags = if (exposicao.prioridadeAtiva) FocusMeteringAction.FLAG_AF
        else FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
        val acao = FocusMeteringAction.Builder(ponto, flags)
            .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        cam.cameraControl.startFocusAndMetering(acao)
    }

    // Liga/desliga a prioridade de obturador (Rua = liga; Pro = desliga). Devolve se foi aplicada.
    fun prioridadeObturador(ativar: Boolean, denominador: Int): Boolean {
        return if (ativar) exposicao.ativar(denominador) else { exposicao.desativar(); false }
    }

    fun desligar() {
        provider?.unbindAll()
        camera = null
        imageCapture = null
    }
}
