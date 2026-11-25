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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Migração 1→2 (aditiva): adiciona a coluna da cota grátis. Nunca destrutiva — preservar o
// desbloqueio de compra do usuário. Ver regra da migração aditiva na doc técnica.
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE configuracao_usuario ADD COLUMN fotos_gratis_usadas INTEGER NOT NULL DEFAULT 0")
    }
}

// Migração 2→3 (aditiva): focal do Rua (28/35/40) + formato da foto (3:2 padrão). Paridade iOS 1.4.1.
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE configuracao_usuario ADD COLUMN focal_rua_mm INTEGER NOT NULL DEFAULT 28")
        db.execSQL("ALTER TABLE configuracao_usuario ADD COLUMN proporcao_rua_3x2 INTEGER NOT NULL DEFAULT 1")
    }
}

@Database(
    entities = [ConfiguracaoUsuario::class, Receita::class],
    version = 3,
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
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instancia = it }
            }
    }
}
