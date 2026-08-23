// RecipeUniforms.kt — snapshot imutável dos parâmetros de uma Receita, já convertidos para os
// valores numéricos que o shader consome (regra 11.6). É o equivalente Android do `RecipeValues`
// do iOS: aqui é onde o encoding MEIO-PASSO ×2 é desfeito (÷2) e onde cada estágio do
// RecipePipeline.swift vira um uniform. NÃO ler os campos ×2 direto — passar sempre por aqui.
//
// forceMonochrome: desatura só o PREVIEW (P&B do Modo Rua); não persiste no arquivo (mesma regra
// do iOS). Quando true, mesmo a receita "Neutra" é desaturada (por isso `neutra` considera isso).

package com.dablab.iseelife.filtros

import com.dablab.iseelife.dados.EfeitoColorChrome
import com.dablab.iseelife.dados.FiltroMono
import com.dablab.iseelife.dados.IntensidadeGrao
import com.dablab.iseelife.dados.Receita
import com.dablab.iseelife.dados.TamanhoGrao

class RecipeUniforms private constructor(
    val neutra: Boolean,             // early-return: devolve o frame cru
    val wbGain: FloatArray,          // ganho por canal do WB (temp+tinta) — vec3
    val rbGain: FloatArray,          // ganho por canal do shift R/B (Fuji, v1.1) — vec3
    val shadowShift: Float,          // deslocamento da curva de tom nas sombras
    val highlightShift: Float,       // deslocamento da curva de tom nas altas luzes
    val mono: Boolean,               // monocromático (receita) OU preview P&B forçado
    val monoWeights: FloatArray,     // pesos de luminância do filtro colorido — vec3
    val saturation: Float,           // fator de saturação (colorida)
    val chrome: Float,               // fator de compressão do color chrome (1 = sem efeito)
    val clarity: Float,              // clareza normalizada [-1..+1] com intensidade já embutida
    val sharpness: Float,            // nitidez normalizada [-1..+1]
    val grainOpacity: Float,         // opacidade do grão (0..0.18)
    val grainScale: Float,           // escala do ruído (1.0 = fino; 2.6 = grão grande)
) {
    companion object {
        // Constrói os uniforms a partir da entidade Receita (regra 11.6). Toda a matemática espelha
        // o RecipePipeline.swift; os comentários citam o estágio equivalente.
        fun de(receita: Receita, forceMonochrome: Boolean = false): RecipeUniforms {
            // ×2 → valor real (meio-passo). Ex.: highlight guardado -2 → real -1,0.
            val hl = receita.highlight / 2.0f
            val sh = receita.shadow / 2.0f
            val sat = receita.saturation / 2.0f

            // ehNeutra (iOS): todos os parâmetros neutros. forceMono desliga o early-return.
            val ehNeutra = hl == 0f && sh == 0f && sat == 0f && receita.whiteBalanceKelvin == null &&
                receita.tint == 0 && receita.grainIntensity == IntensidadeGrao.NENHUM &&
                receita.sharpness == 0 && !receita.monochrome &&
                receita.redAdjust == 0 && receita.blueAdjust == 0 &&
                receita.colorChrome == EfeitoColorChrome.DESLIGADO &&
                receita.colorChromeBlue == EfeitoColorChrome.DESLIGADO && receita.clarity == 0

            // --- WB (temperatura + tinta) — APROXIMAÇÃO do temperatureAndTint do iOS por ganho de
            // canal (Core Image faz adaptação cromática; aqui é ganho perceptual, calibrar no
            // device). tempK alto = mais quente (R↑, B↓); tinta>0 = leve magenta (G↓). ---
            val tempK = receita.whiteBalanceKelvin ?: 6500
            val warmth = ((tempK - 6500) / 6500.0f) * 0.6f          // + = quente
            val tintG = 1.0f - (receita.tint / 9.0f) * 0.10f        // eixo verde/magenta no G
            val wbGain = floatArrayOf(1.0f + warmth, tintG, 1.0f - warmth)

            // --- Shift R/B (Fuji "Red & Blue", v1.1) — EXATO do iOS: ganho ±0.18 no extremo. ---
            val rGain = 1.0f + (receita.redAdjust / 9.0f) * 0.18f
            val bGain = 1.0f + (receita.blueAdjust / 9.0f) * 0.18f
            val rbGain = floatArrayOf(rGain, 1.0f, bGain)

            // --- Curva de tom (highlight/shadow) — EXATO do iOS: ±0.12 nos pontos 0.25/0.75. ---
            val shadowShift = sh / 4.0f * 0.12f
            val highlightShift = hl / 4.0f * 0.12f

            // --- Saturação / monocromático + filtro colorido — EXATO do iOS. ---
            val mono = receita.monochrome || forceMonochrome
            val weights = pesosMono(if (receita.monochrome) receita.monoFilter else FiltroMono.NENHUM)
            val saturation = 1.0f + (sat / 4.0f) * 0.5f

            // --- Color chrome — EXATO do iOS: total = peso(quente) + peso(azul)*0.7; fator 1-total. ---
            val total = pesoChrome(receita.colorChrome) + pesoChrome(receita.colorChromeBlue) * 0.7f
            val chrome = (1.0f - total).coerceIn(0.0f, 1.0f)

            // --- Clareza — iOS: >0 unsharp intensidade (clareza/4)*0.6; <0 leve blur. Normalizo
            // para [-1..1] já com a intensidade do lado positivo embutida. ---
            val clarityReal = receita.clarity / 4.0f                 // [-1..1]
            val clarity = if (clarityReal > 0f) clarityReal * 0.6f else clarityReal

            // --- Nitidez — iOS: >0 sharpen (nitidez/4); <0 blur. Normalizo [-1..1]. ---
            val sharpness = receita.sharpness / 4.0f

            // --- Grão — EXATO do iOS: opacidade por intensidade; escala 2.6 se grão grande. ---
            val grainOpacity = when (receita.grainIntensity) {
                IntensidadeGrao.NENHUM -> 0.0f
                IntensidadeGrao.FRACO -> 0.05f
                IntensidadeGrao.MEDIO -> 0.11f
                IntensidadeGrao.FORTE -> 0.18f
            }
            val grainScale = if (receita.grainSize == TamanhoGrao.GRANDE) 2.6f else 1.0f

            return RecipeUniforms(
                neutra = ehNeutra && !forceMonochrome,
                wbGain = wbGain, rbGain = rbGain,
                shadowShift = shadowShift, highlightShift = highlightShift,
                mono = mono, monoWeights = weights,
                saturation = saturation, chrome = chrome,
                clarity = clarity, sharpness = sharpness,
                grainOpacity = grainOpacity, grainScale = grainScale,
            )
        }

        // Pesos de luminância que simulam o filtro colorido de fotografia P&B (regra 11.6).
        private fun pesosMono(filtro: FiltroMono?): FloatArray = when (filtro ?: FiltroMono.NENHUM) {
            FiltroMono.NENHUM -> floatArrayOf(0.299f, 0.587f, 0.114f)   // Rec.601
            FiltroMono.VERMELHO -> floatArrayOf(0.80f, 0.10f, 0.10f)    // céu escuro, pele clara
            FiltroMono.AMARELO -> floatArrayOf(0.50f, 0.40f, 0.10f)     // contraste suave
            FiltroMono.VERDE -> floatArrayOf(0.10f, 0.70f, 0.20f)       // folhagem/pele
        }

        private fun pesoChrome(e: EfeitoColorChrome): Float = when (e) {
            EfeitoColorChrome.DESLIGADO -> 0.0f
            EfeitoColorChrome.FRACO -> 0.05f
            EfeitoColorChrome.FORTE -> 0.11f
        }
    }
}
