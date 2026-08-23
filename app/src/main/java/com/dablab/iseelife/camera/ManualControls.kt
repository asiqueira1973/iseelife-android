// ManualControls.kt — controles manuais do Modo Pro (épico 8.5). Espelha o ManualCameraControls do
// iOS, mas via Camera2Interop (Camera2CameraControl): ISO, obturador, EV, foco (lensPosition 0..1)
// e WB (temperatura + tinta). SEM abertura (iPhone/Android têm íris fixa — limitação de hardware).
//
// Modelo do Pro no Android: manual pleno. Ao engajar, desliga AE/AWB/AF e escreve os valores do
// sensor. Como o Camera2 não combina EV-compensation com sensor manual, o EV é aplicado aqui como
// um trim em STOPS sobre o ISO (EV +1 = 2× ISO) — divergência documentada (a doc técnica registra).
//
// Requer MANUAL_SENSOR/MANUAL_POST_PROCESSING; sem suporte, os setters são no-op (o app segue no
// automático — fragmentação de câmera, risco seção 23).

package com.dablab.iseelife.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.RggbChannelVector
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import kotlin.math.pow

// Faixas para alimentar os sliders da UI (épico 8.5).
data class FaixasManuais(
    val isoMin: Int,
    val isoMax: Int,
    val obturadorMinNs: Long,
    val obturadorMaxNs: Long,
    val evMin: Float,
    val evMax: Float,
    val suportado: Boolean,
)

@OptIn(ExperimentalCamera2Interop::class)
class ManualControls {

    private var control: Camera2CameraControl? = null
    private var isoRange = 100..800
    private var expRangeNs = 62_500L..250_000_000L   // ~1/16000 .. 1/4 (fallback)
    private var focoMinDist = 0f                       // dioptrias no ponto mais próximo
    private var suportado = false

    // Estado corrente dos controles (valor real aplicado).
    private var iso = 200
    private var obturadorNs = 8_000_000L               // ~1/125 default
    private var ev = 0f
    private var foco = 0f                               // 0 = infinito, 1 = mais próximo
    private var tempK = 5200
    private var tint = 0
    private var wbManual = false

    fun configurar(camera: Camera): FaixasManuais {
        control = Camera2CameraControl.from(camera.cameraControl)
        val info = Camera2CameraInfo.from(camera.cameraInfo)
        val caps = info.getCameraCharacteristic(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
        suportado = caps?.contains(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR
        ) == true
        info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)?.let {
            isoRange = it.lower..it.upper
        }
        info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)?.let {
            // Limita o teto a 1/4 s (uso de mão livre em rua).
            expRangeNs = it.lower..minOf(it.upper, 250_000_000L)
        }
        focoMinDist = info.getCameraCharacteristic(
            CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE
        ) ?: 0f
        iso = ((isoRange.first + isoRange.last) / 2)
        return FaixasManuais(
            isoMin = isoRange.first, isoMax = isoRange.last,
            obturadorMinNs = expRangeNs.first, obturadorMaxNs = expRangeNs.last,
            evMin = -3f, evMax = 3f, suportado = suportado,
        )
    }

    fun definirISO(v: Int) { iso = v.coerceIn(isoRange.first, isoRange.last); aplicarExposicao() }
    fun definirObturador(ns: Long) { obturadorNs = ns.coerceIn(expRangeNs.first, expRangeNs.last); aplicarExposicao() }
    fun definirEV(v: Float) { ev = v.coerceIn(-3f, 3f); aplicarExposicao() }

    // Foco manual: 0 = infinito, 1 = mais próximo (LENS_FOCUS_DISTANCE em dioptrias).
    fun definirFoco(pos: Float) {
        foco = pos.coerceIn(0f, 1f)
        val ctrl = control ?: return
        if (!suportado) return
        ctrl.setCaptureRequestOptions(
            CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                .setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, foco * focoMinDist)
                .build()
        )
    }

    // WB manual: temperatura (Kelvin) + tinta (verde/magenta), via ganhos rggb aproximados.
    fun definirWhiteBalance(temperatura: Int, tinta: Int) {
        tempK = temperatura.coerceIn(2500, 8000)
        tint = tinta.coerceIn(-9, 9)
        wbManual = true
        val ctrl = control ?: return
        if (!suportado) return
        ctrl.setCaptureRequestOptions(
            CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
                .setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
                .setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_GAINS, ganhosDe(tempK, tint))
                .build()
        )
    }

    // "Auto" (épico 8.5) — restaura foco/exposição/WB automáticos e zera o EV (paridade com o
    // restaurarAutomatico do iOS). A UI relê os valores mostrados nos sliders depois.
    fun restaurarAutomatico() {
        val ctrl = control ?: return
        ev = 0f; wbManual = false
        ctrl.setCaptureRequestOptions(
            CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
                .build()
        )
    }

    // Aplica ISO (com trim de EV em stops) + obturador, com AE desligado.
    private fun aplicarExposicao() {
        val ctrl = control ?: return
        if (!suportado) return
        val isoEfetivo = (iso * 2f.pow(ev)).toInt().coerceIn(isoRange.first, isoRange.last)
        ctrl.setCaptureRequestOptions(
            CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, obturadorNs)
                .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, isoEfetivo)
                .build()
        )
    }

    // Ganhos rggb aproximados a partir de Kelvin + tinta. Quente (Kelvin alto) → mais R, menos B;
    // tinta>0 → leve magenta (reduz o verde). Aproximação — calibrar no device.
    private fun ganhosDe(kelvin: Int, tinta: Int): RggbChannelVector {
        val t = (kelvin - 2500f) / (8000f - 2500f)        // 0 (frio) .. 1 (quente)
        val r = 1.4f + t * 1.2f                             // ~1.4 .. 2.6
        val b = 2.6f - t * 1.2f                             // ~2.6 .. 1.4
        val g = 1.0f + (tinta / 9f) * 0.15f                 // tinta no verde
        return RggbChannelVector(r, g, g, b)
    }
}
