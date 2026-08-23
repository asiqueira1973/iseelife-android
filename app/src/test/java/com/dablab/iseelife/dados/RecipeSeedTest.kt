// RecipeSeedTest.kt — testes unitários (JVM puro) das regras do catálogo (regra 11.2/11.5) e do
// encoding ×2 (regra 11.2). Usa um ReceitaDao falso em memória — não precisa de device/Room real.

package com.dablab.iseelife.dados

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// DAO falso: guarda receitas numa lista em memória, replicando o contrato do ReceitaDao.
private class FakeReceitaDao : ReceitaDao {
    val store = mutableListOf<Receita>()
    override fun observarTodas(): Flow<List<Receita>> = flowOf(store.sortedBy { it.displayOrder })
    override suspend fun listarTodas(): List<Receita> = store.sortedBy { it.displayOrder }
    override suspend fun buscarPorNome(nome: String): Receita? = store.firstOrNull { it.displayName == nome }
    override suspend fun buscarPorId(id: String): Receita? = store.firstOrNull { it.id == id }
    override suspend fun inserir(receita: Receita) {
        store.removeAll { it.id == receita.id }   // REPLACE por pk (mesmo contrato do Room)
        store.add(receita)
    }
    override suspend fun atualizar(receita: Receita) {
        val i = store.indexOfFirst { it.id == receita.id }
        if (i >= 0) store[i] = receita
    }
    override suspend fun remover(receita: Receita) { store.removeAll { it.id == receita.id } }
    override suspend fun contar(): Int = store.size
}

class RecipeSeedTest {

    // Banco vazio → sincronizar cria as 6 receitas na ordem certa (regra 11.5).
    @Test fun bancoVazio_criaSeisReceitasNaOrdem() = runBlocking {
        val dao = FakeReceitaDao()
        RecipeSeed.sincronizarCatalogo(dao)
        val nomes = dao.listarTodas().map { it.displayName }
        assertEquals(listOf("Neutra", "Classic", "Soft", "Mono", "Positivo", "Vívida"), nomes)
    }

    // Rodar duas vezes não duplica (upsert por nome, regra 11.5).
    @Test fun sincronizarDuasVezes_naoDuplica() = runBlocking {
        val dao = FakeReceitaDao()
        RecipeSeed.sincronizarCatalogo(dao)
        RecipeSeed.sincronizarCatalogo(dao)
        assertEquals(6, dao.contar())
    }

    // Upsert preserva o `id` da linha existente → receita_ativa_id continua válido (regra 11.5).
    @Test fun upsert_preservaId() = runBlocking {
        val dao = FakeReceitaDao()
        // Simula uma "Classic" já existente com id fixo e valores antigos.
        val idAntigo = "id-classic-antigo"
        dao.inserir(Receita(id = idAntigo, displayName = "Classic", inspiration = "x", displayOrder = 99))
        RecipeSeed.sincronizarCatalogo(dao)
        val classic = dao.buscarPorNome("Classic")
        assertNotNull(classic)
        assertEquals(idAntigo, classic!!.id)          // id preservado
        assertEquals(2, classic.displayOrder)          // valores atualizados p/ canônicos
        assertEquals(-2, classic.highlight)            // ×2 (real -1)
    }

    // Receita FIXA fora do catálogo é deletada; a criada pelo usuário é preservada (regra 11.5).
    @Test fun deletaNaoCanonicaFixa_preservaDoUsuario() = runBlocking {
        val dao = FakeReceitaDao()
        dao.inserir(Receita(displayName = "Retrô", inspiration = "x", displayOrder = 50, createdByUser = false))
        dao.inserir(Receita(displayName = "MinhaReceita", inspiration = "x", displayOrder = 51, createdByUser = true))
        RecipeSeed.sincronizarCatalogo(dao)
        assertNull(dao.buscarPorNome("Retrô"))                 // fixa removida
        assertNotNull(dao.buscarPorNome("MinhaReceita"))        // do usuário preservada
        assertEquals(7, dao.contar())                          // 6 canônicas + 1 do usuário
    }

    // Encoding ×2 (regra 11.2): os valores de seed batem com os do iOS (valor REAL dobrado).
    @Test fun encodingMeioPasso_valoresDeSeed() {
        val por = RecipeSeed.receitasPadrao().associateBy { it.displayName }
        // Classic real -1/+1/+2 → store -2/+2/+4
        assertEquals(-2, por["Classic"]!!.highlight)
        assertEquals(2, por["Classic"]!!.shadow)
        assertEquals(4, por["Classic"]!!.saturation)
        // Positivo real -3/+3/+3 → store -6/+6/+6
        assertEquals(-6, por["Positivo"]!!.highlight)
        assertEquals(6, por["Positivo"]!!.shadow)
        // Vívida real sh +0,5 → store +1; campos v1.1
        assertEquals(1, por["Vívida"]!!.shadow)
        assertEquals(2, por["Vívida"]!!.redAdjust)
        assertEquals(-5, por["Vívida"]!!.blueAdjust)
        assertEquals(EfeitoColorChrome.FORTE, por["Vívida"]!!.colorChrome)
        // Neutra é toda neutra (raw)
        val n = por["Neutra"]!!
        assertTrue(n.highlight == 0 && n.shadow == 0 && n.saturation == 0)
        assertNull(n.whiteBalanceKelvin)
        assertFalse(n.monochrome)
        // Mono usa filtro vermelho
        assertEquals(FiltroMono.VERMELHO, por["Mono"]!!.monoFilter)
        assertTrue(por["Mono"]!!.monochrome)
    }
}
