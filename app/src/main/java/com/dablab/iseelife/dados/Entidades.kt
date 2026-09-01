// Entidades.kt — entidades Room (spec seção 7). Convenção 18: propriedades em inglês, COLUNAS em
// português snake_case (via @ColumnInfo) — a fronteira de idioma fica aqui. Duas entidades
// independentes, sem relação (a "receita ativa" é ligada por valor em receita_ativa_id).

package com.dablab.iseelife.dados

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

// configuracao_usuario — linha única (singleton, id sempre = 1). Preferências locais do usuário.
// Nunca excluída no MVP; atualizada in-place. Defaults espelham a seção 7.
@Entity(tableName = "configuracao_usuario")
data class ConfiguracaoUsuario(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: Int = 1,                       // sempre 1 (singleton)

    @ColumnInfo(name = "idioma_preferido") val language: Idioma = Idioma.PT,
    @ColumnInfo(name = "modo_padrao") val defaultMode: ModoCaptura = ModoCaptura.RUA,
    @ColumnInfo(name = "ultima_lente_usada") val lastLens: LenteCamera? = null,
    @ColumnInfo(name = "grid_ativado") val gridEnabled: Boolean = true,
    @ColumnInfo(name = "nivel_ativado") val levelEnabled: Boolean = true,
    @ColumnInfo(name = "obturador_silencioso") val silentShutter: Boolean = true,
    @ColumnInfo(name = "preview_pb_ativado") val previewBwEnabled: Boolean = false,
    @ColumnInfo(name = "compra_desbloqueada") val purchaseUnlocked: Boolean = false,
    @ColumnInfo(name = "data_compra") val purchaseDate: Long? = null,          // epoch ms
    @ColumnInfo(name = "onboarding_concluido") val onboardingDone: Boolean = false,
    @ColumnInfo(name = "receita_ativa_id") val activeRecipeId: String? = null, // uuid, por valor
    @ColumnInfo(name = "rua_obturador_denominador") val streetShutterDenominator: Int = 250,
    // Cota grátis (regra 11.4): fotos salvas de graça antes do paywall. Assim o app é totalmente
    // usável sem pagamento/login (o revisor da Play acessa tudo), e é "experimente antes de comprar".
    @ColumnInfo(name = "fotos_gratis_usadas") val freeSavesUsed: Int = 0,
)

// receita — catálogo fixo, somente-leitura no MVP. Cada campo alimenta o shader (regra 11.6).
// IMPORTANTE (regra 11.2): highlight/shadow/saturation ficam em MEIO-PASSO ×2 (valor dobrado);
// a camada de uniforms do shader divide por 2. NÃO ler estes campos como valor real direto.
@Entity(
    tableName = "receita",
    indices = [
        Index(value = ["nome_exibicao"], unique = true),
        Index(value = ["ordem_exibicao"]),
    ],
)
data class Receita(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "nome_exibicao") val displayName: String,
    @ColumnInfo(name = "inspiracao") val inspiration: String,

    @ColumnInfo(name = "highlight") val highlight: Int = 0,     // meio-passo ×2
    @ColumnInfo(name = "shadow") val shadow: Int = 0,           // meio-passo ×2
    @ColumnInfo(name = "saturacao") val saturation: Int = 0,    // meio-passo ×2
    @ColumnInfo(name = "temperatura_wb_kelvin") val whiteBalanceKelvin: Int? = null,
    @ColumnInfo(name = "tinta_wb") val tint: Int = 0,           // eixo verde/magenta
    @ColumnInfo(name = "intensidade_grao") val grainIntensity: IntensidadeGrao = IntensidadeGrao.NENHUM,
    @ColumnInfo(name = "nitidez") val sharpness: Int = 0,       // -4..+4
    @ColumnInfo(name = "monocromatico") val monochrome: Boolean = false,
    @ColumnInfo(name = "filtro_mono") val monoFilter: FiltroMono? = null,
    @ColumnInfo(name = "ordem_exibicao") val displayOrder: Int,

    // Fidelidade v1.1
    @ColumnInfo(name = "ajuste_vermelho") val redAdjust: Int = 0,   // ganho canal R
    @ColumnInfo(name = "ajuste_azul") val blueAdjust: Int = 0,      // ganho canal B
    @ColumnInfo(name = "efeito_color_chrome") val colorChrome: EfeitoColorChrome = EfeitoColorChrome.DESLIGADO,
    @ColumnInfo(name = "efeito_color_chrome_azul") val colorChromeBlue: EfeitoColorChrome = EfeitoColorChrome.DESLIGADO,
    @ColumnInfo(name = "clareza") val clarity: Int = 0,            // -4..+4 (≠ nitidez)
    @ColumnInfo(name = "tamanho_grao") val grainSize: TamanhoGrao = TamanhoGrao.PEQUENO,
    @ColumnInfo(name = "criada_pelo_usuario") val createdByUser: Boolean = false,
)
