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

    // As 9 receitas fixas (v1.2, paridade com o iOS), na ordem do seletor. Ordem 1 = Neutra (raw,
    // padrão neutro, regra 11.3). Mantidas: Neutra/Classic/Soft/Retrato 400; novas (Ricoh GR III):
    // Color Film/Americana Color/Analog Film/Monochrome Film/Black & White Negative. Removidas:
    // Mono/Positivo/Vívida (o sincronizarCatalogo apaga na próxima abertura).
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
        // "Retrato 400" — negativo de retrato ISO 400 (Fuji), aprovada no TestFlight iOS.
        Receita(
            displayName = "Retrato 400",
            inspiration = "Negativo de retrato ISO 400",
            highlight = 0, shadow = -4, saturation = 4,   // real 0 / -2 / +2
            whiteBalanceKelvin = 5200, tint = 0,
            grainIntensity = IntensidadeGrao.FORTE,
            sharpness = -2, monochrome = false, monoFilter = null,
            displayOrder = 4,
            redAdjust = 1, blueAdjust = -6,
            colorChrome = EfeitoColorChrome.FORTE,
            colorChromeBlue = EfeitoColorChrome.DESLIGADO,
            clarity = -2, grainSize = TamanhoGrao.PEQUENO,
        ),

        // ---- Receitas inspiradas na Ricoh GR III (Image Control). Mapeamento APROXIMADO (Hue,
        // High/Low Key, Contrast base etc. sem campo no modelo); paridade 1:1 com o iOS.

        // "Color Film" — Effect Standard, natural, sombras suaves.
        Receita(
            displayName = "Color Film",
            inspiration = "Ricoh GR — Standard",
            highlight = 4, shadow = -8, saturation = 2,   // real +2 / -4 / +1
            whiteBalanceKelvin = null, tint = -1,         // WB Auto; comp G:2
            grainIntensity = IntensidadeGrao.NENHUM,
            sharpness = 1, monochrome = false, monoFilter = null,
            displayOrder = 5,
            redAdjust = 5, blueAdjust = -5,               // WB comp A:9 (âmbar)
            colorChrome = EfeitoColorChrome.DESLIGADO,
            colorChromeBlue = EfeitoColorChrome.DESLIGADO,
            clarity = -1,
        ),
        // "Americana Color" — Positive Film, saturada/contrastada, WB quente (CT 5250K).
        Receita(
            displayName = "Americana Color",
            inspiration = "Ricoh GR — Positive Film",
            highlight = -6, shadow = 8, saturation = 4,   // real -3 / +4 / +2
            whiteBalanceKelvin = 5250, tint = -3,         // CT 5250K; comp G:5
            grainIntensity = IntensidadeGrao.NENHUM,
            sharpness = 1, monochrome = false, monoFilter = null,
            displayOrder = 6,
            redAdjust = 5, blueAdjust = -5,               // WB comp A:10 (âmbar)
            colorChrome = EfeitoColorChrome.FRACO,
            colorChromeBlue = EfeitoColorChrome.DESLIGADO,
            clarity = 2,
        ),
        // "Analog Film" — Positive Film bem saturada (Sat +4), WB 6300K.
        Receita(
            displayName = "Analog Film",
            inspiration = "Ricoh GR — Positive Film",
            highlight = -8, shadow = 8, saturation = 8,   // real -4 / +4 / +4
            whiteBalanceKelvin = 6300, tint = -3,         // CT 6300K; comp G:6
            grainIntensity = IntensidadeGrao.NENHUM,
            sharpness = 0, monochrome = false, monoFilter = null,
            displayOrder = 7,
            redAdjust = 1, blueAdjust = -1,               // WB comp A:1 (âmbar leve)
            colorChrome = EfeitoColorChrome.FRACO,
            colorChromeBlue = EfeitoColorChrome.DESLIGADO,
            clarity = -2,
        ),
        // "Monochrome Film" — BW Monotone, contraste baixo, grão leve.
        Receita(
            displayName = "Monochrome Film",
            inspiration = "Ricoh GR — BW Monotone",
            highlight = -8, shadow = -8, saturation = 0,  // real -4 / -4 / 0
            whiteBalanceKelvin = null, tint = 0,
            grainIntensity = IntensidadeGrao.FRACO,       // Grain Effect 1
            sharpness = 1, monochrome = true, monoFilter = null,
            displayOrder = 8,
            clarity = 2, grainSize = TamanhoGrao.PEQUENO,
        ),
        // "Black & White Negative" — Hi-Contrast B&W, highlights suaves, grão presente.
        Receita(
            displayName = "Black & White Negative",
            inspiration = "Ricoh GR — Hi-Contrast B&W",
            highlight = -8, shadow = 0, saturation = 0,   // real -4 / 0 / 0
            whiteBalanceKelvin = null, tint = 0,
            grainIntensity = IntensidadeGrao.MEDIO,       // Grain Effect 2
            sharpness = -1, monochrome = true, monoFilter = null,
            displayOrder = 9,
            clarity = 2, grainSize = TamanhoGrao.PEQUENO,
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
