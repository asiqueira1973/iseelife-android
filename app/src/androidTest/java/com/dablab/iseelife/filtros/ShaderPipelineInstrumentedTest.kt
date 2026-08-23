// ShaderPipelineInstrumentedTest.kt — teste INSTRUMENTADO (roda em device/emulador, precisa de GPU
// real). Valida que o shader GLSL do épico 8.2 compila e produz a saída esperada, aplicando o
// OffscreenRecipeRenderer a bitmaps sólidos:
//  - Neutra: devolve a imagem praticamente intacta (early-return / raw).
//  - Mono (filtro vermelho): converte para cinza com os pesos (0.80, 0.10, 0.10).
//
// Rodar: ./gradlew connectedDebugAndroidTest  (com um emulador/aparelho conectado).

package com.dablab.iseelife.filtros

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dablab.iseelife.dados.RecipeSeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class ShaderPipelineInstrumentedTest {

    private fun bitmapSolido(r: Int, g: Int, b: Int, w: Int = 4, h: Int = 4): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.rgb(r, g, b))
        return bmp
    }

    private val porNome = RecipeSeed.receitasPadrao().associateBy { it.displayName }

    @Test fun neutra_devolveImagemQuaseIntacta() {
        val renderer = OffscreenRecipeRenderer()
        try {
            val entrada = bitmapSolido(100, 150, 200)
            val saida = renderer.aplicar(entrada, porNome["Neutra"]!!)
            val p = saida.getPixel(2, 2)
            // Tolerância p/ arredondamento GL. Neutra é raw → praticamente igual.
            assertTrue(abs(Color.red(p) - 100) <= 4)
            assertTrue(abs(Color.green(p) - 150) <= 4)
            assertTrue(abs(Color.blue(p) - 200) <= 4)
        } finally {
            renderer.liberar()
        }
    }

    @Test fun mono_convertePorPesosDoFiltroVermelho() {
        val renderer = OffscreenRecipeRenderer()
        try {
            // Mono não tem grão/nitidez que atrapalhem num bitmap sólido; foco no cinza dos pesos.
            val entrada = bitmapSolido(200, 50, 50)
            val saida = renderer.aplicar(entrada, porNome["Mono"]!!)
            val p = saida.getPixel(2, 2)
            // cinza = 0.80*200 + 0.10*50 + 0.10*50 = 170 (antes da curva de tom/grão; tolerância maior).
            val esperado = 170
            assertEquals(esperado.toFloat(), Color.red(p).toFloat(), 20f)
            // Monocromático: R≈G≈B.
            assertTrue(abs(Color.red(p) - Color.green(p)) <= 3)
            assertTrue(abs(Color.green(p) - Color.blue(p)) <= 3)
        } finally {
            renderer.liberar()
        }
    }
}
