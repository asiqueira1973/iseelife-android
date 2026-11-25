// CameraController.kt — liga o CameraX ao preview GL e concentra os controles de câmera (épicos
// 8.3/8.4). Vincula dois use cases: Preview (frames → SurfaceTexture → shader) e ImageAnalysis
// (luminância → loop de ISO da prioridade de obturador). Expõe foco por toque (Modo Rua) e a
// ativação/desativação da prioridade de obturador. O binding respeita o ciclo de vida.

package com.dablab.iseelife.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.util.Log
import android.view.OrientationEventListener
import android.view.Surface
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
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
    // Orientação FÍSICA do aparelho (sensor) → targetRotation do ImageCapture. A UI é travada em
    // retrato, mas a FOTO sai na orientação em que o telefone é segurado (paridade com o iOS 1.4).
    private var orientationListener: OrientationEventListener? = null
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
        iniciarOrientationListener()
    }

    // Acompanha a orientação física do aparelho e ajusta o targetRotation da foto (a UI segue em
    // retrato). Assim a foto sai em paisagem quando o telefone está na horizontal.
    private fun iniciarOrientationListener() {
        if (orientationListener != null) return
        orientationListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val rotation = when (orientation) {
                    in 45 until 135 -> Surface.ROTATION_270
                    in 135 until 225 -> Surface.ROTATION_180
                    in 225 until 315 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                imageCapture?.targetRotation = rotation
            }
        }.also { if (it.canDetectOrientation()) it.enable() }
    }

    // Captura em ALTA resolução (foto do sensor) e aplica a receita em resolução cheia via o mesmo
    // shader do preview (WYSIWYG). Devolve o Bitmap filtrado na main thread — ou `null` se não houver
    // ImageCapture disponível ou der erro, para o chamador cair no fallback (frame do preview).
    fun capturarAltaResolucao(receita: Receita, forceMono: Boolean, tresPorDois: Boolean, onResult: (Bitmap?) -> Unit) {
        val captura = imageCapture ?: run { onResult(null); return }
        val main = ContextCompat.getMainExecutor(context)
        captura.takePicture(capturaExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val bruto = image.toBitmap()
                    val rot = image.imageInfo.rotationDegrees
                    image.close()
                    // Roda o bitmap para retrato (upright) conforme a rotação do sensor.
                    val emPeBruto = if (rot != 0) {
                        val m = Matrix().apply { postRotate(rot.toFloat()) }
                        Bitmap.createBitmap(bruto, 0, 0, bruto.width, bruto.height, m, true)
                            .also { if (it != bruto) bruto.recycle() }
                    } else bruto
                    val emPe = if (tresPorDois) cortarPara3x2(emPeBruto) else emPeBruto
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

    // Focal do Modo Rua (28/35/40 mm equivalentes) como zoom na lente ATUAL; null = nativo (1×).
    // O zoom sai do campo de visão REAL do sensor (tamanho físico ativo + focal da lente), não de
    // uma focal suposta. Equivalência pelo LADO LONGO do quadro 3:2 de 35mm (36mm): o lado longo da
    // foto cobre o mesmo que o de uma Ricoh GR/Leica na mesma focal (paridade iOS 1.4.1).
    @OptIn(ExperimentalCamera2Interop::class)
    fun aplicarFocal(mm: Int?) {
        val cam = camera ?: return
        val zoom = cam.cameraInfo.zoomState.value
        val minZ = maxOf(zoom?.minZoomRatio ?: 1f, 1f)
        val maxZ = zoom?.maxZoomRatio ?: 1f
        val alvo = if (mm == null) 1f else (razaoZoom(Camera2CameraInfo.from(cam.cameraInfo), mm) ?: 1f)
        val r = alvo.coerceIn(minZ, maxOf(minZ, maxZ))
        Log.d("ISeeLife", "focal=${mm}mm zoom=$r (alvo=$alvo)")
        cam.cameraControl.setZoomRatio(r)
    }

    // tan(meio-ângulo nativo do lado longo) / tan(meio-ângulo alvo de 36mm na focal pedida).
    @OptIn(ExperimentalCamera2Interop::class)
    private fun razaoZoom(info: Camera2CameraInfo, mm: Int): Float? {
        val fisico = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return null
        val focal = info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            ?.minOrNull() ?: return null
        val ativo = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        val pixels = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
        val fracao = if (ativo != null && pixels != null && pixels.width > 0) ativo.width().toFloat() / pixels.width else 1f
        val ladoLongoMm = maxOf(fisico.width, fisico.height) * fracao
        if (focal <= 0f || ladoLongoMm <= 0f) return null
        val tanNativo = (ladoLongoMm / 2f) / focal
        val tanAlvo = 18f / mm
        return tanNativo / tanAlvo
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
        orientationListener?.disable()
        orientationListener = null
        provider?.unbindAll()
        camera = null
        imageCapture = null
    }
}

// Corte central para 3:2 mantendo o LADO LONGO (a focal equivalente é calibrada nele). Vale para
// retrato e paisagem: corta sempre o lado curto. 4000x3000 -> 4000x2667.
fun cortarPara3x2(src: Bitmap): Bitmap {
    val w = src.width; val h = src.height
    val (cw, ch) = if (w >= h) w to Math.round(w * 2f / 3f) else Math.round(h * 2f / 3f) to h
    if (cw >= w && ch >= h) return src
    val x = (w - cw) / 2; val y = (h - ch) / 2
    return Bitmap.createBitmap(src, x, y, minOf(cw, w), minOf(ch, h)).also { if (it != src) src.recycle() }
}
