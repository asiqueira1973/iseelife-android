// SettingsScreen.kt — configurações do app (épico 8.9). Lê/escreve configuracao_usuario: grade,
// nível, obturador silencioso, preview P&B, piso do obturador do Rua e restaurar compra. Acessível
// pela engrenagem no visor (nos dois modos). O idioma é tratado no épico 8.11 (troca em runtime).

package com.dablab.iseelife.telas.configuracoes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import com.dablab.iseelife.R
import com.dablab.iseelife.dados.ConfiguracaoUsuario
import com.dablab.iseelife.dados.Idioma
import com.dablab.iseelife.designsystem.Estilo
import com.dablab.iseelife.plataforma.Idiomas

@Composable
fun SettingsScreen(
    config: ConfiguracaoUsuario,
    aoSalvar: (ConfiguracaoUsuario) -> Unit,
    comprado: Boolean,
    aoRestaurar: () -> Unit,
    aoFechar: () -> Unit,
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier.fillMaxSize().background(Estilo.FundoVisor.copy(alpha = 0.96f)),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.config_titulo),
                color = Estilo.TextoPrimario,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            LinhaToggle(stringResource(R.string.config_grid), config.gridEnabled) {
                aoSalvar(config.copy(gridEnabled = it))
            }
            LinhaToggle(stringResource(R.string.config_nivel), config.levelEnabled) {
                aoSalvar(config.copy(levelEnabled = it))
            }
            LinhaToggle(stringResource(R.string.config_silencioso), config.silentShutter) {
                aoSalvar(config.copy(silentShutter = it))
            }
            LinhaToggle(stringResource(R.string.config_preview_pb), config.previewBwEnabled) {
                aoSalvar(config.copy(previewBwEnabled = it))
            }

            // Idioma (épico 8.11) — troca em runtime (recreate da Activity).
            Text(
                text = stringResource(R.string.config_idioma),
                color = Estilo.TextoSecundario,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((lang, rotulo) in listOf("pt" to "Português", "en" to "English")) {
                    val ativo = config.language == Idioma.de(lang)
                    Text(
                        text = rotulo,
                        color = if (ativo) Estilo.FundoVisor else Estilo.TextoPrimario,
                        fontWeight = if (ativo) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier
                            .background(if (ativo) Estilo.Accent else Estilo.Painel, RoundedCornerShape(10.dp))
                            .clickable {
                                Idiomas.definirIdioma(context, lang)
                                aoSalvar(config.copy(language = Idioma.de(lang)))
                                (context as? Activity)?.recreate()
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }

            // Formato da foto no Modo Rua (3:2 padrão / 4:3). O Pro é sempre 4:3.
            Text(
                text = stringResource(R.string.config_formato_rua),
                color = Estilo.TextoSecundario,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((tres, rotulo) in listOf(true to "3:2", false to "4:3")) {
                    val ativo = config.street3x2 == tres
                    Text(
                        text = rotulo,
                        color = if (ativo) Estilo.FundoVisor else Estilo.TextoPrimario,
                        fontWeight = if (ativo) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier
                            .background(if (ativo) Estilo.Accent else Estilo.Painel, RoundedCornerShape(10.dp))
                            .clickable { aoSalvar(config.copy(street3x2 = tres)) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
            Text(
                text = stringResource(R.string.config_formato_rua_dica),
                color = Estilo.TextoSecundario,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp),
            )

            // Piso do obturador do Modo Rua (250/500/1000).
            Text(
                text = stringResource(R.string.config_piso_obturador),
                color = Estilo.TextoSecundario,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (denom in listOf(250, 500, 1000)) {
                    val ativo = config.streetShutterDenominator == denom
                    Text(
                        text = "1/$denom",
                        color = if (ativo) Estilo.FundoVisor else Estilo.TextoPrimario,
                        fontWeight = if (ativo) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier
                            .background(if (ativo) Estilo.Accent else Estilo.Painel, RoundedCornerShape(10.dp))
                            .clickable { aoSalvar(config.copy(streetShutterDenominator = denom)) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }

            // Compra: restaurar ou status.
            if (comprado) {
                Text(
                    text = stringResource(R.string.config_comprado),
                    color = Estilo.Accent,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 24.dp),
                )
            } else {
                Text(
                    text = stringResource(R.string.config_restaurar_compra),
                    color = Estilo.TextoPrimario,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .padding(top = 24.dp)
                        .clickable { aoRestaurar() }
                        .padding(vertical = 8.dp),
                )
            }

            // Fechar.
            Text(
                text = stringResource(R.string.config_fechar),
                color = Estilo.FundoVisor,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(top = 24.dp)
                    .fillMaxWidth()
                    .background(Estilo.Accent, RoundedCornerShape(14.dp))
                    .clickable { aoFechar() }
                    .padding(vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun LinhaToggle(rotulo: String, ligado: Boolean, aoMudar: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(rotulo, color = Estilo.TextoPrimario, fontSize = 16.sp)
        Switch(
            checked = ligado,
            onCheckedChange = aoMudar,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Estilo.FundoVisor,
                checkedTrackColor = Estilo.Accent,
            ),
        )
    }
}
