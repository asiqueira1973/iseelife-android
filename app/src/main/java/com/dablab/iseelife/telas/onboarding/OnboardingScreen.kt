// OnboardingScreen.kt — boas-vindas + pedido de permissão de câmera (épico 8.8). Ao final (permissão
// concedida ou negada), chama `aoConcluir`, que grava onboarding_concluido e roteia para o visor ou
// para a tela de permissão negada. Aparece uma única vez (critério 8.8).

package com.dablab.iseelife.telas.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dablab.iseelife.R
import com.dablab.iseelife.designsystem.Estilo

@Composable
fun OnboardingScreen(aoConcluir: () -> Unit) {
    // Ao responder a permissão (sim ou não), conclui o onboarding — o roteamento decide o destino.
    val pedirPermissao = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { aoConcluir() }

    Column(
        modifier = Modifier.fillMaxSize().background(Estilo.FundoVisor).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(120.dp),
        )
        Text(
            text = stringResource(R.string.app_name),
            color = Estilo.TextoPrimario,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.onboarding_subtitulo),
            color = Estilo.TextoSecundario,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp, bottom = 40.dp),
        )
        Text(
            text = stringResource(R.string.onboarding_comecar),
            color = Estilo.FundoVisor,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(Estilo.Accent, RoundedCornerShape(14.dp))
                .clickable { pedirPermissao.launch(Manifest.permission.CAMERA) }
                .padding(vertical = 16.dp),
        )
    }
}
