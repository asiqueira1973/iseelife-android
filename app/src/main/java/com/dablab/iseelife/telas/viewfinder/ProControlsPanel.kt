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
    var iso by remember { mutableFloatStateOf(((faixas.isoMin + faixas.isoMax) / 2).toFloat()) }
    var obtT by remember { mutableFloatStateOf(0.5f) }     // 0..1 → tempo de exposição (log)
    var ev by remember { mutableFloatStateOf(0f) }
    var temp by remember { mutableFloatStateOf(5200f) }
    var tint by remember { mutableFloatStateOf(0f) }

    // Mapeia o slider 0..1 para tempo de exposição (log entre min e max) e devolve o denominador.
    fun nsDe(t: Float): Long {
        val lnMin = ln(faixas.obturadorMinNs.toDouble())
        val lnMax = ln(faixas.obturadorMaxNs.toDouble())
        return exp(lnMin + (lnMax - lnMin) * t).toLong()
    }
    fun denominador(ns: Long): Int = (1_000_000_000.0 / ns).roundToInt().coerceAtLeast(1)

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
        LinhaSlider("ISO", iso.roundToInt().toString(), iso, faixas.isoMin.toFloat()..faixas.isoMax.toFloat()) {
            iso = it; aoISO(it.roundToInt())
        }
        LinhaSlider("Obturador", "1/${denominador(nsDe(obtT))}", obtT, 0f..1f) {
            obtT = it; aoObturador(nsDe(it))
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

        // Botão "Auto" (restaura automático + zera EV).
        Text(
            text = "Auto",
            color = Estilo.Accent,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable { ev = 0f; aoAuto() }
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
            colors = SliderDefaults.colors(
                thumbColor = Estilo.Accent,
                activeTrackColor = Estilo.Accent,
            ),
        )
    }
}
