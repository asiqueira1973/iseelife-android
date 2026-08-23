// MainActivity.kt — Activity única (épico 8.0). Nesta fase de FUNDAÇÃO ela só: (a) roda a rotina
// de abertura (garante o singleton de config + sincroniza o catálogo — regra 11.5) e (b) mostra
// uma tela preta com a contagem de receitas, para validar o épico 8.1. As telas reais
// (onboarding, visor, etc.) substituem este conteúdo nos épicos seguintes.

package com.dablab.iseelife

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import com.dablab.iseelife.dados.Receita
import com.dablab.iseelife.dados.Repositorio

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repositorio = (application as App).repositorio
        setContent {
            MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) {
                FundacaoScreen(repositorio)
            }
        }
    }
}

// Tela temporária de fundação: prova que o banco/seed/sincronização funcionam (épico 8.1).
@Composable
private fun FundacaoScreen(repositorio: Repositorio) {
    // Roda a rotina de abertura uma vez (idempotente): cria a config e sincroniza o catálogo.
    LaunchedEffect(Unit) { repositorio.prepararNaAbertura() }

    val receitas: List<Receita> by repositorio.observarReceitas().collectAsState(initial = emptyList())

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            val linhas = receitas.joinToString("\n") { "${it.displayOrder}. ${it.displayName}" }
            Text(
                text = if (receitas.isEmpty()) "Carregando…"
                       else "I See Life — fundação OK\n${receitas.size} receitas:\n$linhas",
                color = Color(0xFFFFB000),
                textAlign = TextAlign.Center,
            )
        }
    }
}
