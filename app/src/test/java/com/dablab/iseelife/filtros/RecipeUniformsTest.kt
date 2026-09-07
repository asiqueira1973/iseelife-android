// RecipeUniformsTest.kt — testes JVM da conversão Receita → uniforms (regra 11.6). Valida o ×2 do
// meio-passo, o early-return da Neutra, o preview P&B forçado e os ganhos/pesos de cada estágio,
// sem precisar de contexto GL.

package com.dablab.iseelife.filtros

import com.dablab.iseelife.dados.RecipeSeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeUniformsTest {

    private val porNome = RecipeSeed.receitasPadrao().associateBy { it.displayName }
    private val D = 0.0005f

    @Test fun neutra_isNeutraEEarlyReturn() {
        val u = RecipeUniforms.de(porNome["Neutra"]!!)
        assertTrue(u.neutra)
    }

    @Test fun neutra_comPreviewPb_desliga_earlyReturn_e_forcaMono() {
        val u = RecipeUniforms.de(porNome["Neutra"]!!, forceMonochrome = true)
        assertFalse(u.neutra)       // com P&B forçado não é mais early-return
        assertTrue(u.mono)          // e vira monocromático (só no preview)
    }

    @Test fun classic_tomESaturacao_batemComOiOS() {
        val u = RecipeUniforms.de(porNome["Classic"]!!)
        assertFalse(u.neutra)
        // real: hl -1, sh +1, sat +2  → shifts ±0.03; saturação 1.25
        assertEquals(0.03f, u.shadowShift, D)
        assertEquals(-0.03f, u.highlightShift, D)
        assertEquals(1.25f, u.saturation, D)
        // WB 7000K "quente": R sobe, B desce
        assertTrue(u.wbGain[0] > 1.0f)
        assertTrue(u.wbGain[2] < 1.0f)
        // grão fraco
        assertEquals(0.05f, u.grainOpacity, D)
    }

    @Test fun mono_semFiltro_usaPesosRec601() {
        // Catálogo v1.2: as receitas P&B não usam filtro colorido → pesos Rec.601.
        val u = RecipeUniforms.de(porNome["Monochrome Film"]!!)
        assertTrue(u.mono)
        assertEquals(0.299f, u.monoWeights[0], D)
        assertEquals(0.587f, u.monoWeights[1], D)
        assertEquals(0.114f, u.monoWeights[2], D)
        assertEquals(0.05f, u.grainOpacity, D)  // Grain Effect 1 → fraco
    }

    @Test fun retrato400_shiftRB_chrome_clareza() {
        val u = RecipeUniforms.de(porNome["Retrato 400"]!!)
        // R/B: rGain 1+(1/9)*0.18 ; bGain 1+(-6/9)*0.18
        assertEquals(1.0f + (1f / 9f) * 0.18f, u.rbGain[0], D)
        assertEquals(1.0f + (-6f / 9f) * 0.18f, u.rbGain[2], D)
        // color chrome forte (0.11) → fator 0.89
        assertEquals(0.89f, u.chrome, D)
        // clareza -2 → real -0.5 (lado negativo não multiplica por 0.6)
        assertEquals(-0.5f, u.clarity, D)
    }
}
