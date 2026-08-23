// Enums.kt — enums do modelo de dados (spec seção 7).
// Cada enum carrega `valor`: a string EXATA gravada no banco (pt snake_case, paridade com o iOS).
// Os nomes das constantes seguem a convenção Kotlin (MAIÚSCULAS); o banco guarda `valor`.
// `de(valor)` reconstrói a constante a partir da string do banco (usado nos TypeConverters).

package com.dablab.iseelife.dados

// Idioma da interface (idioma_preferido).
enum class Idioma(val valor: String) {
    PT("pt"), EN("en");
    companion object { fun de(v: String) = entries.first { it.valor == v } }
}

// Modo de captura ao abrir o visor (modo_padrao). Rua = padrão (princípio 0.1).
enum class ModoCaptura(val valor: String) {
    RUA("rua"), PRO("pro");
    companion object { fun de(v: String) = entries.first { it.valor == v } }
}

// Lente traseira usada (ultima_lente_usada).
enum class LenteCamera(val valor: String) {
    WIDE("wide"), ULTRA_WIDE("ultra_wide");
    companion object { fun de(v: String) = entries.first { it.valor == v } }
}

// Força do grão procedural (intensidade_grao) — regra 11.6.
enum class IntensidadeGrao(val valor: String) {
    NENHUM("nenhum"), FRACO("fraco"), MEDIO("medio"), FORTE("forte");
    companion object { fun de(v: String) = entries.first { it.valor == v } }
}

// Filtro colorido simulado da receita monocromática (filtro_mono) — regra 11.6.
enum class FiltroMono(val valor: String) {
    NENHUM("nenhum"), AMARELO("amarelo"), VERMELHO("vermelho"), VERDE("verde");
    companion object { fun de(v: String) = entries.first { it.valor == v } }
}

// Efeito Color Chrome — compressão de saturação nos tons saturados (v1.1) — regra 11.6.
enum class EfeitoColorChrome(val valor: String) {
    DESLIGADO("desligado"), FRACO("fraco"), FORTE("forte");
    companion object { fun de(v: String) = entries.first { it.valor == v } }
}

// Tamanho do grão (v1.1) — regra 11.6.
enum class TamanhoGrao(val valor: String) {
    PEQUENO("pequeno"), GRANDE("grande");
    companion object { fun de(v: String) = entries.first { it.valor == v } }
}
