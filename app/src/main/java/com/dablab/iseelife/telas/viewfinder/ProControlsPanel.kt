// ProControlsPanel.kt — painel de controles manuais do Modo Pro (épico 8.5). Sliders de foco, ISO,
// obturador, EV e WB (temperatura + tinta) + botão "Auto". Sem abertura (hardware). O painel tem
// altura limitada e rola; os Sliders do Material arrastam na horizontal e não conflitam com o
// scroll vertical (resolve, no Android, o problema que exigiu um slider custom no iOS).

package com.dablab.iseelife.telas.viewfinder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.dablab.iseelife.R
import com.dablab.iseelife.camera.FaixasManuais
import com.dablab.iseelife.designsystem.Estilo
import kotlin.math.ln
import kotlin.math.exp
import kotlin.math.roundToInt

@Composable
fun ProControlsPanel(
    faixas: FaixasManuais,
    aoISO: (Int) -> Unit,
    aoObturador: (Long) -> Unit,
    aoEV: (Float) -> Unit,
    aoFoco: (Float) -> Unit,
    aoWhiteBalance: (Int, Int) -> Unit,
    aoAuto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Estados dos sliders (valores de UI; iniciam em pontos neutros).
    var foco by remember { mutableFloatStateOf(0f) }
    // ISO em escala log (0..1); obturador como ÍNDICE nas velocidades padrão de câmera.
    var isoT by remember { mutableFloatStateOf(0.3f) }

    var ev by remember { mutableFloatStateOf(0f) }
    var temp by remember { mutableFloatStateOf(5200f) }
    var tint by remember { mutableFloatStateOf(0f) }

    // ISO: escala log entre min e max (cada stop ocupa o mesmo espaço).
    fun isoDe(t: Float): Int {
        val lnMin = ln(faixas.isoMin.toDouble()); val lnMax = ln(faixas.isoMax.toDouble())
        return exp(lnMin + (lnMax - lnMin) * t).roundToInt()
    }
    // Obturador: velocidades padrão (1/3 de stop) que cabem na faixa do sensor — 1/500 fica fácil.
    val velocidades = remember(faixas) {
        VELOCIDADES_PADRAO.filter { d ->
            val ns = 1_000_000_000L / d
            ns >= faixas.obturadorMinNs && ns <= faixas.obturadorMaxNs
        }.ifEmpty { listOf(125) }
    }
    fun denomDe(i: Float): Int = velocidades[i.roundToInt().coerceIn(0, velocidades.lastIndex)]
    // Começa em 1/125 (índice do primeiro denominador <= 125, já que a lista vai do mais lento ao mais rápido).
    var obtI by remember(velocidades) {
        mutableFloatStateOf(velocidades.indexOfFirst { it >= 125 }.coerceAtLeast(0).toFloat())
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Estilo.Painel, RoundedCornerShape(16.dp))
            .heightIn(max = 260.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LinhaSlider("Foco", if (foco <= 0.01f) "∞" else "%.2f".format(foco), foco, 0f..1f) {
            foco = it; aoFoco(it)
        }
        LinhaSlider("ISO", isoDe(isoT).toString(), isoT, 0f..1f) {
            isoT = it; aoISO(isoDe(it))
        }
        LinhaSlider(
            "Obturador", "1/${denomDe(obtI)}", obtI, 0f..velocidades.lastIndex.toFloat().coerceAtLeast(1f),
            passos = (velocidades.size - 2).coerceAtLeast(0),
        ) {
            val antes = denomDe(obtI)
            obtI = it
            val d = denomDe(it)
            if (d != antes) aoObturador(1_000_000_000L / d)
        }
        LinhaSlider("EV", (if (ev >= 0) "+%.1f" else "%.1f").format(ev), ev, -3f..3f) {
            ev = it; aoEV(it)
        }
        LinhaSlider("WB Temp", "${temp.roundToInt()}K", temp, 2500f..8000f) {
            temp = it; aoWhiteBalance(temp.roundToInt(), tint.roundToInt())
        }
        LinhaSlider("WB Tinta", tint.roundToInt().toString(), tint, -9f..9f) {
            tint = it; aoWhiteBalance(temp.roundToInt(), tint.roundToInt())
        }

        // Botão "Restaurar" (volta tudo pro automático + zera EV e os sliders).
        Text(
            text = stringResource(R.string.pro_restaurar),
            color = Estilo.Accent,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable {
                    ev = 0f; foco = 0f; temp = 5200f; tint = 0f; isoT = 0.3f
                    obtI = velocidades.indexOfFirst { it >= 125 }.coerceAtLeast(0).toFloat()
                    aoAuto()
                }
                .padding(vertical = 6.dp, horizontal = 20.dp),
        )
    }
}

// Uma linha do painel: rótulo + valor + slider horizontal.
@Composable
private fun LinhaSlider(
    rotulo: String,
    valor: String,
    posicao: Float,
    faixa: ClosedFloatingPointRange<Float>,
    passos: Int = 0,
    aoMudar: (Float) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(rotulo, color = Estilo.TextoSecundario, fontSize = 13.sp)
            Text(valor, color = Estilo.TextoPrimario, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Slider(
            value = posicao,
            onValueChange = aoMudar,
            valueRange = faixa,
            steps = passos,
            colors = SliderDefaults.colors(
                thumbColor = Estilo.Accent,
                activeTrackColor = Estilo.Accent,
            ),
        )
    }
}

// Velocidades padrão de câmera em 1/3 de stop (denominadores), do mais lento ao mais rápido. Antes
// a régua era contínua e era difícil voltar a um valor redondo como 1/500.
private val VELOCIDADES_PADRAO = listOf(
    30, 40, 50, 60, 80, 100, 125, 160, 200, 250, 320, 400, 500, 640, 800, 1000, 1250, 1600, 2000,
    2500, 3200, 4000, 5000, 6400, 8000,
)
