// MainActivity.kt — Activity única (épico 8.0/8.3). Roda a rotina de abertura (garante o singleton
// de config + sincroniza o catálogo de receitas — regra 11.5) e exibe o visor com preview filtrado
// (ViewfinderScreen). Onboarding/permissão dedicada e roteamento completo chegam no épico 8.8.

package com.dablab.iseelife

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import com.dablab.iseelife.telas.RootView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repositorio = (application as App).repositorio
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                // Prepara o banco na 1ª composição (idempotente): singleton + sincroniza catálogo.
                LaunchedEffect(Unit) { repositorio.prepararNaAbertura() }
                RootView()
            }
        }
    }
}
