// Daos.kt — Data Access Objects do Room (spec seção 7). Consultas em SQL usam os nomes de COLUNA
// em português; a API Kotlin é em inglês. Comentários indicam a regra/épico que cada uma serve.

package com.dablab.iseelife.dados

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// Acesso ao catálogo de receitas (regra 11.2/11.5).
@Dao
interface ReceitaDao {

    // Todas as receitas na ordem fixa do seletor (ordem_exibicao). Observável para a UI.
    @Query("SELECT * FROM receita ORDER BY ordem_exibicao ASC")
    fun observarTodas(): Flow<List<Receita>>

    // Leitura pontual (sincronização/seed).
    @Query("SELECT * FROM receita ORDER BY ordem_exibicao ASC")
    suspend fun listarTodas(): List<Receita>

    // Busca por nome_exibicao — identidade estável usada no upsert do catálogo (regra 11.5).
    @Query("SELECT * FROM receita WHERE nome_exibicao = :nome LIMIT 1")
    suspend fun buscarPorNome(nome: String): Receita?

    @Query("SELECT * FROM receita WHERE id = :id LIMIT 1")
    suspend fun buscarPorId(id: String): Receita?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserir(receita: Receita)

    @Update
    suspend fun atualizar(receita: Receita)

    @Delete
    suspend fun remover(receita: Receita)

    @Query("SELECT COUNT(*) FROM receita")
    suspend fun contar(): Int
}

// Acesso ao singleton de configuração (spec seção 7). Sempre id = 1.
@Dao
interface ConfiguracaoUsuarioDao {

    @Query("SELECT * FROM configuracao_usuario WHERE id = 1 LIMIT 1")
    fun observar(): Flow<ConfiguracaoUsuario?>

    @Query("SELECT * FROM configuracao_usuario WHERE id = 1 LIMIT 1")
    suspend fun obter(): ConfiguracaoUsuario?

    // Insere a linha singleton se ainda não existir (1º uso); ignora se já existe.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun inserirSeAusente(config: ConfiguracaoUsuario)

    @Update
    suspend fun atualizar(config: ConfiguracaoUsuario)
}
