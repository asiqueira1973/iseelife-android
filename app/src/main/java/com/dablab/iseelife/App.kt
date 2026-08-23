// App.kt — classe Application (épico 8.0). Ponto único de criação do banco e do repositório,
// reaproveitados por toda a sessão (equivalente aos @StateObject do RootView no iOS).

package com.dablab.iseelife

import android.app.Application
import com.dablab.iseelife.compras.BillingManager
import com.dablab.iseelife.dados.AppDatabase
import com.dablab.iseelife.dados.Repositorio

class App : Application() {
    // Criados sob demanda (lazy) na 1ª necessidade e mantidos vivos com o processo.
    val database: AppDatabase by lazy { AppDatabase.obter(this) }
    val repositorio: Repositorio by lazy { Repositorio(database) }
    val billing: BillingManager by lazy { BillingManager(this) }

    // Disparo compartilhado (épico 8.10): o visor registra sua função de disparar aqui, e a
    // MainActivity chama ao receber os botões de volume. Null quando o visor não está ativo.
    @Volatile var disparador: (() -> Unit)? = null
}
