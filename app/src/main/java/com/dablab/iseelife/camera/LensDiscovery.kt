// LensDiscovery.kt — detecção das lentes traseiras disponíveis (regra 11.1). Enumera as câmeras
// traseiras via CameraX e distingue WIDE de ULTRA_WIDE pela menor distância focal (a ultra-wide
// tem a focal mais curta). Devolve, para cada lente presente, um CameraSelector que fixa aquele
// câmera físico. Espelha o LensDiscovery.swift do iOS: só lentes FISICAMENTE presentes; com uma
// só lente, a UI oculta o botão de troca (não desabilita).

package com.dablab.iseelife.camera

import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import android.hardware.camera2.CameraCharacteristics
import com.dablab.iseelife.dados.LenteCamera

@OptIn(ExperimentalCamera2Interop::class)
class LensDiscovery(provider: ProcessCameraProvider) {

    data class Lente(val tipo: LenteCamera, val seletor: CameraSelector)

    // Lentes presentes, começando pela WIDE (padrão). Se só houver uma, a lista tem 1 item.
    val lentes: List<Lente>

    init {
        // Câmeras traseiras com sua menor distância focal (mm).
        val traseiras = provider.availableCameraInfos
            .filter { it.lensFacing == CameraSelector.LENS_FACING_BACK }
            .mapNotNull { info ->
                val id = Camera2CameraInfo.from(info).cameraId
                val focal = Camera2CameraInfo.from(info)
                    .getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    ?.minOrNull()
                if (focal != null) Triple(id, focal, info) else null
            }
            .sortedByDescending { it.second }   // focal maior primeiro = wide; menor = ultra-wide

        lentes = when {
            traseiras.isEmpty() ->
                // Fallback (risco seção 23): sem enumeração, usa a traseira padrão como wide.
                listOf(Lente(LenteCamera.WIDE, CameraSelector.DEFAULT_BACK_CAMERA))
            traseiras.size == 1 ->
                listOf(Lente(LenteCamera.WIDE, seletorParaId(traseiras[0].first)))
            else -> listOf(
                Lente(LenteCamera.WIDE, seletorParaId(traseiras.first().first)),
                Lente(LenteCamera.ULTRA_WIDE, seletorParaId(traseiras.last().first)),
            )
        }
    }

    // Há mais de uma lente? (a UI só mostra o botão de troca quando true — regra 11.1).
    fun temMultiplas(): Boolean = lentes.size > 1

    fun seletor(tipo: LenteCamera): CameraSelector =
        lentes.firstOrNull { it.tipo == tipo }?.seletor ?: lentes.first().seletor

    // CameraSelector que fixa o câmera físico de um cameraId específico (Camera2).
    private fun seletorParaId(id: String): CameraSelector =
        CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .addCameraFilter { infos -> infos.filter { Camera2CameraInfo.from(it).cameraId == id } }
            .build()
}
