// Repositorio.kt — fachada de acesso a dados (spec seção 4). Concentra o bootstrap do singleton de
// configuração e a sincronização do catálogo na abertura, além dos fluxos observáveis para a UI.

package com.dablab.iseelife.dados

import kotlinx.coroutines.flow.Flow

class Repositorio(private val db: AppDatabase) {

    private val receitaDao = db.receitaDao()
    private val configDao = db.configuracaoUsuarioDao()

    // Fluxos observáveis (a UI coleta) — épicos 8.3/8.9.
    fun observarReceitas(): Flow<List<Receita>> = receitaDao.observarTodas()
    fun observarConfiguracao(): Flow<ConfiguracaoUsuario?> = configDao.observar()

    // Rotina de abertura (RootView equivalente do iOS): garante o singleton e sincroniza o
    // catálogo fixo (regra 11.5). Deve rodar antes de exibir o visor.
    suspend fun prepararNaAbertura() {
        configDao.inserirSeAusente(ConfiguracaoUsuario())   // cria a linha id=1 no 1º uso
        RecipeSeed.sincronizarCatalogo(receitaDao)
    }

    suspend fun obterConfiguracao(): ConfiguracaoUsuario? = configDao.obter()
    suspend fun atualizarConfiguracao(config: ConfiguracaoUsuario) = configDao.atualizar(config)
    suspend fun buscarReceita(id: String): Receita? = receitaDao.buscarPorId(id)
}
