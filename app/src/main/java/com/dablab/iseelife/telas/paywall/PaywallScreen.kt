// PaywallScreen.kt — paywall da compra única (épico 8.7 / regra 11.4). Aparece no 1º disparo se
// a compra não estiver desbloqueada; a foto fica pendente até a compra confirmar. Preço vem da
// Play (formattedPrice), nunca hardcoded. Sobreposição escura sobre o visor.

package com.dablab.iseelife.telas.paywall

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.dablab.iseelife.R
import com.dablab.iseelife.designsystem.Estilo

@Composable
fun PaywallScreen(
    precoFormatado: String?,
    aoComprar: () -> Unit,
    aoRestaurar: () -> Unit,
    aoFechar: () -> Unit,
    aoSimular: (() -> Unit)? = null,   // só em debug
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Estilo.FundoVisor.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = stringResource(R.string.paywall_titulo),
                color = Estilo.TextoPrimario,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.paywall_beneficio),
                color = Estilo.TextoSecundario,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )

            // Botão de compra (mostra o preço da Play quando disponível).
            val rotuloCompra = stringResource(R.string.paywall_comprar) +
                (precoFormatado?.let { " · $it" } ?: "")
            Botao(rotuloCompra, destaque = true, onClick = aoComprar)

            Text(
                text = stringResource(R.string.paywall_restaurar),
                color = Estilo.TextoPrimario,
                fontSize = 14.sp,
                modifier = Modifier.clickable { aoRestaurar() }.padding(8.dp),
            )
            Text(
                text = stringResource(R.string.paywall_fechar),
                color = Estilo.TextoSecundario,
                fontSize = 14.sp,
                modifier = Modifier.clickable { aoFechar() }.padding(8.dp),
            )

            // Simulação de compra — só em builds de debug (validação do gate sem a Play).
            if (aoSimular != null) {
                Text(
                    text = "(debug) simular compra",
                    color = Estilo.Accent,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { aoSimular() }.padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun Botao(texto: String, destaque: Boolean, onClick: () -> Unit) {
    Text(
        text = texto,
        color = if (destaque) Estilo.FundoVisor else Estilo.TextoPrimario,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (destaque) Estilo.Accent else Estilo.Painel, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 16.dp),
    )
}
