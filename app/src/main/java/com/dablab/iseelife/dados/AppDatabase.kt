// AppDatabase.kt — banco Room local (spec seção 4/7). Duas entidades independentes; sem lógica no
// banco (sem triggers/funções) — toda regra vive no código Kotlin. Migração é ADITIVA: colunas
// novas entram com default; NUNCA trocar o tipo de uma coluna existente (evita quebra de update,
// mesmo motivo do ×2 do meio-passo — ver regra 11.5/DOCUMENTACAO-TECNICA).

package com.dablab.iseelife.dados

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [ConfiguracaoUsuario::class, Receita::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Conversores::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun receitaDao(): ReceitaDao
    abstract fun configuracaoUsuarioDao(): ConfiguracaoUsuarioDao

    companion object {
        @Volatile private var instancia: AppDatabase? = null

        // Singleton do banco (caro de criar). Reaproveitado em todo o app.
        fun obter(context: Context): AppDatabase =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "iseelife.db",
                ).build().also { instancia = it }
            }
    }
}
