// Estilo.kt — constantes visuais compartilhadas (spec seção 16, paridade com o DesignSystem do
// iOS). Fundo do visor preto; accent âmbar #FFB000 só para significado (foco travado, receita
// ativa, toggle ligado) — nunca decorativo.

package com.dablab.iseelife.designsystem

import androidx.compose.ui.graphics.Color

object Estilo {
    val Accent = Color(0xFFFFB000)
    val FundoVisor = Color(0xFF000000)
    val Painel = Color(0xCC1A1A1A)          // painel translúcido escuro
    val TextoSecundario = Color(0xFF9E9E9E)
    val TextoPrimario = Color(0xFFFFFFFF)
}
