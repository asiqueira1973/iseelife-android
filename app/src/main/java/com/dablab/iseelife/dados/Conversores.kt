// Conversores.kt — TypeConverters do Room (spec seção 7).
// Convertem cada enum para a string `valor` (pt) gravada no banco e de volta. Enums nulos
// (filtro_mono, ultima_lente_usada) convertem null <-> null.

package com.dablab.iseelife.dados

import androidx.room.TypeConverter

class Conversores {
    @TypeConverter fun idiomaParaTexto(e: Idioma): String = e.valor
    @TypeConverter fun textoParaIdioma(v: String): Idioma = Idioma.de(v)

    @TypeConverter fun modoParaTexto(e: ModoCaptura): String = e.valor
    @TypeConverter fun textoParaModo(v: String): ModoCaptura = ModoCaptura.de(v)

    @TypeConverter fun lenteParaTexto(e: LenteCamera?): String? = e?.valor
    @TypeConverter fun textoParaLente(v: String?): LenteCamera? = v?.let { LenteCamera.de(it) }

    @TypeConverter fun graoParaTexto(e: IntensidadeGrao): String = e.valor
    @TypeConverter fun textoParaGrao(v: String): IntensidadeGrao = IntensidadeGrao.de(v)

    @TypeConverter fun filtroMonoParaTexto(e: FiltroMono?): String? = e?.valor
    @TypeConverter fun textoParaFiltroMono(v: String?): FiltroMono? = v?.let { FiltroMono.de(it) }

    @TypeConverter fun chromeParaTexto(e: EfeitoColorChrome): String = e.valor
    @TypeConverter fun textoParaChrome(v: String): EfeitoColorChrome = EfeitoColorChrome.de(v)

    @TypeConverter fun tamGraoParaTexto(e: TamanhoGrao): String = e.valor
    @TypeConverter fun textoParaTamGrao(v: String): TamanhoGrao = TamanhoGrao.de(v)
}
