// LumaAnalyzer.kt — analisador de luminância média do frame (épico 8.4). Alimenta o loop de ISO da
// prioridade de obturador: com o obturador fixo, o ISO é ajustado para manter a cena num brilho
// alvo. Amostra esparsamente o plano Y (luminância) do frame YUV, o que é barato. Throttle para não
// rodar a cada frame. Espelha a ideia do exposureTargetOffset usado no loop de ISO do iOS.

package com.dablab.iseelife.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

class LumaAnalyzer(
    // Recebe a luminância média normalizada [0..1] da cena, já com throttle.
    private val aoLuma: (Float) -> Unit,
) : ImageAnalysis.Analyzer {

    private var ultimo = 0L
    private val intervaloMs = 120L   // ~8 Hz, como o loop amortecido do iOS

    override fun analyze(image: ImageProxy) {
        val agora = System.currentTimeMillis()
        if (agora - ultimo < intervaloMs) { image.close(); return }
        ultimo = agora

        // Plano Y (luminância). Amostra 1 a cada N pixels para custo baixo.
        val plano = image.planes[0]
        val buffer = plano.buffer
        val rowStride = plano.rowStride
        val pixelStride = plano.pixelStride
        val w = image.width
        val h = image.height

        var soma = 0L
        var conta = 0
        val passo = 16
        var y = 0
        while (y < h) {
            val linha = y * rowStride
            var x = 0
            while (x < w) {
                val idx = linha + x * pixelStride
                if (idx < buffer.limit()) {
                    soma += (buffer.get(idx).toInt() and 0xFF)
                    conta++
                }
                x += passo
            }
            y += passo
        }
        image.close()

        if (conta > 0) aoLuma((soma.toFloat() / conta) / 255f)
    }
}
