// RecipeSelectorSheet.kt — seletor de receitas (épico 8.9). Lista as 6 receitas do catálogo; toque
// escolhe (escreve receita_ativa_id via callback) e fecha. A receita ativa fica em destaque âmbar.
// Sobreposição inferior; tocar fora fecha.

package com.dablab.iseelife.telas.viewfinder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dablab.iseelife.R
import com.dablab.iseelife.dados.Receita
import com.dablab.iseelife.designsystem.Estilo

@Composable
fun RecipeSelectorSheet(
    receitas: List<Receita>,
    ativaId: String?,
    aoSelecionar: (String) -> Unit,
    aoFechar: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Estilo.FundoVisor.copy(alpha = 0.75f))
            .clickable { aoFechar() },   // tocar fora fecha
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Estilo.Painel, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.seletor_titulo),
                color = Estilo.TextoSecundario,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            for (r in receitas) {
                val ativa = r.id == ativaId
                Row2(r, ativa) { aoSelecionar(r.id) }
            }
        }
    }
}

// Uma linha do seletor: nome + inspiração, com destaque âmbar quando ativa.
@Composable
private fun Row2(receita: Receita, ativa: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (ativa) Modifier.border(1.5.dp, Estilo.Accent, RoundedCornerShape(12.dp))
                else Modifier
            )
            .background(Estilo.FundoVisor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = receita.displayName,
            color = if (ativa) Estilo.Accent else Estilo.TextoPrimario,
            fontSize = 17.sp,
            fontWeight = if (ativa) FontWeight.SemiBold else FontWeight.Normal,
        )
        Text(
            text = receita.inspiration,
            color = Estilo.TextoSecundario,
            fontSize = 13.sp,
        )
    }
}
