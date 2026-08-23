// PermissionDeniedScreen.kt — tela mostrada quando a permissão de câmera foi negada (épico 8.8 /
// fluxo de exceção seção 9). Oferece abrir as configurações do sistema ou tentar de novo.

package com.dablab.iseelife.telas.permissoes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dablab.iseelife.R
import com.dablab.iseelife.designsystem.Estilo

@Composable
fun PermissionDeniedScreen(aoAbrirConfig: () -> Unit, aoTentar: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Estilo.FundoVisor).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.permissao_negada_titulo),
            color = Estilo.TextoPrimario,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.permissao_negada_texto),
            color = Estilo.TextoSecundario,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp, bottom = 32.dp),
        )
        Text(
            text = stringResource(R.string.permissao_abrir_config),
            color = Estilo.FundoVisor,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(Estilo.Accent, RoundedCornerShape(14.dp))
                .clickable { aoAbrirConfig() }
                .padding(vertical = 16.dp),
        )
        Text(
            text = stringResource(R.string.permissao_tentar),
            color = Estilo.TextoPrimario,
            fontSize = 14.sp,
            modifier = Modifier.clickable { aoTentar() }.padding(16.dp),
        )
    }
}
