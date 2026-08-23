// ExposureController.kt — prioridade de obturador no Modo Rua (épico 8.4 / regra 11.7). Espelha o
// enhancement do iOS: obturador FIXO (piso configurável 1/250·1/500·1/1000) + ISO AUTOMÁTICO via um
// loop amortecido que ajusta a sensibilidade para manter a cena num brilho alvo. Usa Camera2Interop
// (Camera2CameraControl) para escrever CaptureRequest.
//
// Requer MANUAL_SENSOR no aparelho; se não houver, `ativar` devolve false e o app segue com a
// exposição automática do CameraX (degradação graciosa — risco seção 23, fragmentação de câmera).

package com.dablab.iseelife.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera

@OptIn(ExperimentalCamera2Interop::class)
class ExposureController {

    private var control: Camera2CameraControl? = null
    private var isoRange: IntRange = 100..800
    private var exposureRangeNs: LongRange = 0L..0L
    private var suporta = false

    @Volatile private var ativo = false
    @Volatile private var exposureNs = 0L
    @Volatile private var isoAtual = 200

    private val isoAlvoLuma = 0.45f     // brilho médio alvo da cena (mid-gray perceptual)
    private val ganhoLoop = 0.6f        // amortecimento do ajuste de ISO

    // Prepara o controlador para a câmera atual; lê as faixas e a capacidade MANUAL_SENSOR.
    fun configurar(camera: Camera) {
        control = Camera2CameraControl.from(camera.cameraControl)
        val info = Camera2CameraInfo.from(camera.cameraInfo)
        val caps = info.getCameraCharacteristic(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
        suporta = caps?.contains(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR
        ) == true
        info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)?.let {
            isoRange = it.lower..it.upper
        }
        info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)?.let {
            exposureRangeNs = it.lower..it.upper
        }
    }

    // Liga a prioridade de obturador com o piso 1/denominador s. Devolve true se aplicada.
    fun ativar(denominador: Int): Boolean {
        val ctrl = control ?: return false
        if (!suporta) return false
        val alvoNs = (1_000_000_000L / denominador)
            .coerceIn(
                if (exposureRangeNs.first > 0) exposureRangeNs.first else 1L,
                if (exposureRangeNs.last > 0) exposureRangeNs.last else Long.MAX_VALUE,
            )
        exposureNs = alvoNs
        isoAtual = ((isoRange.first + isoRange.last) / 2).coerceIn(isoRange.first, isoRange.last)
        aplicar(ctrl)
        ativo = true
        return true
    }

    // Volta para exposição automática (Modo Pro ou ao sair do Rua).
    fun desativar() {
        val ctrl = control ?: return
        ativo = false
        ctrl.setCaptureRequestOptions(
            CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                .build()
        )
    }

    // Recebe a luminância média (do LumaAnalyzer) e ajusta o ISO mantendo o obturador fixo.
    fun aoLuma(luma: Float) {
        if (!ativo) return
        val ctrl = control ?: return
        val erro = isoAlvoLuma - luma                       // cena escura (erro>0) → subir ISO
        val fator = 1f + ganhoLoop * erro
        val novo = (isoAtual * fator).toInt().coerceIn(isoRange.first, isoRange.last)
        if (novo != isoAtual) {
            isoAtual = novo
            aplicar(ctrl)
        }
    }

    val prioridadeAtiva: Boolean get() = ativo

    // Escreve obturador fixo + ISO atual, com AE desligado (senão o AE ignora o manual).
    private fun aplicar(ctrl: Camera2CameraControl) {
        ctrl.setCaptureRequestOptions(
            CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureNs)
                .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, isoAtual)
                .build()
        )
    }
}
