// Idiomas.kt — troca de idioma em runtime (épico 8.11). Guarda a escolha em SharedPreferences
// (leitura síncrona, necessária no attachBaseContext, antes do Room async) e envolve o contexto da
// Activity com o locale escolhido. Nulo = segue o idioma do sistema. A troca aplica com um
// recreate() da Activity — equivalente Android à troca "em tempo real" do iOS (que fazia swap de
// bundle). As strings já estão em res/values (pt-BR) e res/values-en.

package com.dablab.iseelife.plataforma

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object Idiomas {
    private const val ARQUIVO = "iseelife_prefs"
    private const val CHAVE = "idioma"   // "pt" | "en" | null (sistema)

    fun idiomaSalvo(context: Context): String? =
        context.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE).getString(CHAVE, null)

    fun definirIdioma(context: Context, lang: String?) {
        context.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE).edit()
            .putString(CHAVE, lang).apply()
    }

    // Envolve o contexto base com o locale salvo (chamado no attachBaseContext da MainActivity).
    fun aplicar(base: Context): Context {
        val lang = idiomaSalvo(base) ?: return base
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
