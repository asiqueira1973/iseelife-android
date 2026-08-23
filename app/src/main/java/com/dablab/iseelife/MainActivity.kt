// MainActivity.kt — Activity única (épico 8.0/8.3). Roda a rotina de abertura (garante o singleton
// de config + sincroniza o catálogo de receitas — regra 11.5) e exibe o visor com preview filtrado
// (ViewfinderScreen). Onboarding/permissão dedicada e roteamento completo chegam no épico 8.8.

package com.dablab.iseelife

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import com.dablab.iseelife.telas.RootView

class MainActivity : ComponentActivity() {

    // Aplica o idioma escolhido (épico 8.11) antes de inflar a UI.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.dablab.iseelife.plataforma.Idiomas.aplicar(newBase))
    }

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

    // Botões de volume disparam a foto quando o visor está ativo (épico 8.10, paridade iOS). O visor
    // registra sua ação em App.disparador; se não houver (onboarding/config), o volume age normal.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val ehVolume = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if (ehVolume && event.action == KeyEvent.ACTION_DOWN) {
            val disparar = (application as App).disparador
            if (disparar != null) {
                disparar()
                return true   // consome (não altera o volume enquanto fotografa)
            }
        }
        // Consome também o ACTION_UP do volume enquanto o visor está ativo (evita o "bip"/HUD).
        if (ehVolume && (application as App).disparador != null) return true
        return super.dispatchKeyEvent(event)
    }
}
