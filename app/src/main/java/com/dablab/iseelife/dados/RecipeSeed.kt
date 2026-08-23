// RecipeSeed.kt — catálogo fixo de receitas + sincronização (regra 11.2/11.5). Porte 1:1 do
// RecipeSeed.swift do iOS: MESMOS valores, incluindo o encoding MEIO-PASSO ×2 de
// highlight/shadow/saturation (o valor guardado é o DOBRO do real; a camada de uniforms divide
// por 2). Alterar um número aqui muda a cor — manter em paridade com o iOS/planilha 11.2.
//
// Convenção de temperatura_wb_kelvin: Kelvin ASSUMIDO da cena (dial de WB). Baixo esfria/azula,
// alto esquenta; null = segue o WB do sensor.

package com.dablab.iseelife.dados

// Catálogo canônico + rotina de sincronização. Objeto sem estado (só lógica).
object RecipeSeed {

    // As 6 receitas fixas, na ordem do seletor. Ordem 1 = Neutra (raw, padrão neutro, regra 11.3).
    // hl/sh/sat abaixo estão em MEIO-PASSO ×2 (ex.: Classic hl real -1 → -2 aqui).
    fun receitasPadrao(): List<Receita> = listOf(
        // "Neutra" — imagem crua, sem receita. Todos os parâmetros neutros → o shader faz
        // early-return e devolve o frame do sensor intacto (regra 11.3).
        Receita(
            displayName = "Neutra",
            inspiration = "Câmera (sem receita)",
            highlight = 0, shadow = 0, saturation = 0,
            whiteBalanceKelvin = null, tint = 0,
            grainIntensity = IntensidadeGrao.NENHUM,
            sharpness = 0, monochrome = false, monoFilter = null,
            displayOrder = 1,
        ),
        // "Classic" — insp. Classic Chrome. WB "quente" → 7000K.
        Receita(
            displayName = "Classic",
            inspiration = "Classic Chrome",
            highlight = -2, shadow = 2, saturation = 4,   // real -1 / +1 / +2
            whiteBalanceKelvin = 7000, tint = 0,
            grainIntensity = IntensidadeGrao.FRACO,
            sharpness = 2, monochrome = false, monoFilter = null,
            displayOrder = 2,
        ),
        // "Soft" — insp. Astia/Soft. Revisada no device (iOS): WB 5600K + tint +1 (era 2650/-8,
        // que ficava verde). Ver DOCUMENTACAO-TECNICA do iOS (divergência 2b).
        Receita(
            displayName = "Soft",
            inspiration = "Astia/Soft",
            highlight = -4, shadow = 0, saturation = -4,  // real -2 / 0 / -2
            whiteBalanceKelvin = 5600, tint = 1,
            grainIntensity = IntensidadeGrao.NENHUM,
            sharpness = -1, monochrome = false, monoFilter = null,
            displayOrder = 3,
        ),
        // "Mono" — insp. Acros + filtro vermelho. shadow +4 real (moody).
        Receita(
            displayName = "Mono",
            inspiration = "Acros+R",
            highlight = -2, shadow = 8, saturation = 0,   // real -1 / +4 / 0 (sat N/A em mono)
            whiteBalanceKelvin = null, tint = -1,
            grainIntensity = IntensidadeGrao.FORTE,
            sharpness = -2, monochrome = true, monoFilter = FiltroMono.VERMELHO,
            displayOrder = 4,
        ),
        // "Positivo" — insp. Ricoh GR III Positive Film. WB "fria" → 5000K.
        Receita(
            displayName = "Positivo",
            inspiration = "Ricoh GR Positive Film",
            highlight = -6, shadow = 6, saturation = 6,   // real -3 / +3 / +3
            whiteBalanceKelvin = 5000, tint = 3,
            grainIntensity = IntensidadeGrao.NENHUM,
            sharpness = 0, monochrome = false, monoFilter = null,
            displayOrder = 5,
        ),
        // "Vívida" (v1.1) — insp. Kodachrome 64: cores punchy, clareza alta, grão fino. Única das
        // receitas v1.1 mantida (Retrô/Retrato/Dourada removidas após validação no device).
        Receita(
            displayName = "Vívida",
            inspiration = "Kodachrome 64",
            highlight = 0, shadow = 1, saturation = 4,    // real 0 / +0,5 / +2
            whiteBalanceKelvin = 5500, tint = 0,
            grainIntensity = IntensidadeGrao.FRACO,
            sharpness = 1, monochrome = false, monoFilter = null,
            displayOrder = 6,
            redAdjust = 2, blueAdjust = -5,
            colorChrome = EfeitoColorChrome.FORTE,
            colorChromeBlue = EfeitoColorChrome.DESLIGADO,
            clarity = 3, grainSize = TamanhoGrao.PEQUENO,
        ),
    )

    // sincronizarCatalogo (regra 11.5) — roda a cada abertura. O catálogo é dado do CÓDIGO, não do
    // usuário: o banco sempre reflete os valores canônicos. Faz:
    //  1) UPSERT por nome_exibicao (identidade estável): preserva o `id` da linha existente — assim
    //     configuracao_usuario.receita_ativa_id continua válido mesmo quando a ORDEM muda — e
    //     atualiza TODOS os campos (inclusive os da v1.1).
    //  2) DELETE das receitas FIXAS que saíram do catálogo (por nome_exibicao), respeitando as
    //     criadas pelo usuário (createdByUser = true, roadmap).
    suspend fun sincronizarCatalogo(dao: ReceitaDao) {
        val canonicas = receitasPadrao()
        val nomesCanonicos = canonicas.map { it.displayName }.toSet()

        // 1) upsert por nome
        for (canonica in canonicas) {
            val existente = dao.buscarPorNome(canonica.displayName)
            if (existente != null) {
                // Mantém o id existente; substitui o resto pelos valores canônicos.
                dao.atualizar(canonica.copy(id = existente.id))
            } else {
                dao.inserir(canonica)
            }
        }

        // 2) remove fixas fora do catálogo (não toca nas criadas pelo usuário)
        val atuais = dao.listarTodas()
        for (r in atuais) {
            if (!r.createdByUser && r.displayName !in nomesCanonicos) {
                dao.remover(r)
            }
        }
    }
}
