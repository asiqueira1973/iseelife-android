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
import com.dablab.iseelife.dados.FiltroMono
import com.dablab.iseelife.dados.Receita
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

    // Receita SÓ-mono (sem curva de tom/grão/nitidez) para isolar a conversão P&B e conferir o
    // cinza exato dos pesos do filtro vermelho: 0.80*R + 0.10*G + 0.10*B.
    @Test fun monoIsolado_convertePorPesosDoFiltroVermelho() {
        val renderer = OffscreenRecipeRenderer()
        try {
            val soMono = Receita(
                displayName = "MonoTeste", inspiration = "teste",
                monochrome = true, monoFilter = FiltroMono.VERMELHO, displayOrder = 99,
            )
            val entrada = bitmapSolido(200, 50, 50)
            val saida = renderer.aplicar(entrada, soMono)
            val p = saida.getPixel(2, 2)
            // cinza = 0.80*200 + 0.10*50 + 0.10*50 = 170.
            assertEquals(170f, Color.red(p).toFloat(), 6f)
            // Monocromático: R≈G≈B.
            assertTrue(abs(Color.red(p) - Color.green(p)) <= 3)
            assertTrue(abs(Color.green(p) - Color.blue(p)) <= 3)
        } finally {
            renderer.liberar()
        }
    }

    // A receita "Mono" real do catálogo produz imagem monocromática (R≈G≈B), sem checar o valor
    // exato (ela também aplica curva de tom e grão).
    @Test fun monoDoCatalogo_ehMonocromatica() {
        val renderer = OffscreenRecipeRenderer()
        try {
            val saida = renderer.aplicar(bitmapSolido(200, 50, 50), porNome["Mono"]!!)
            val p = saida.getPixel(2, 2)
            val r = Color.red(p); val g = Color.green(p); val b = Color.blue(p)
            // grão forte adiciona ruído leve → tolerância um pouco maior no R≈G≈B.
            assertTrue("RGB=($r,$g,$b)", abs(r - g) <= 8 && abs(g - b) <= 8)
        } finally {
            renderer.liberar()
        }
    }
}
