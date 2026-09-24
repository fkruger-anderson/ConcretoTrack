package br.com.anderson.meuprimeiroprojeto.motorista

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * Testes da formatação de m³ e km para a tela ([formatarM3] e [formatarKm]) no app do motorista.
 *
 * As duas funções usam o idioma do aparelho para escolher vírgula ou ponto. Para o teste dar o
 * mesmo resultado em qualquer computador, o idioma é fixado em português do Brasil antes de cada
 * teste (`@Before`) e o original é devolvido depois (`@After`).
 */
class FormatoM3Test {

    // O idioma que o computador estava usando antes do teste, para devolver no fim.
    private lateinit var idiomaOriginal: Locale

    /** Roda antes de cada teste: guarda o idioma atual e troca para português do Brasil. */
    @Before
    fun usarPortugues() {
        idiomaOriginal = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("pt-BR"))
    }

    /** Roda depois de cada teste: devolve o idioma original, para não afetar outros testes. */
    @After
    fun devolverIdioma() {
        Locale.setDefault(idiomaOriginal)
    }

    /** Quantidade sem parte decimal aparece sem vírgula: 20 e não "20,0". */
    @Test
    fun m3InteiroApareceSemVirgula() {
        assertEquals("20", formatarM3(20.0))
    }

    /** Quantidade quebrada aparece com vírgula e uma casa. */
    @Test
    fun m3QuebradoApareceComVirgula() {
        assertEquals("6,5", formatarM3(6.5))
    }

    /** Km aparece sempre com uma casa decimal, mesmo quando é inteiro. */
    @Test
    fun kmInteiroApareceComUmaCasa() {
        assertEquals("12,0", formatarKm(12.0))
    }

    /** Km com mais casas é arredondado para uma. */
    @Test
    fun kmComMaisCasasEhArredondadoParaUma() {
        assertEquals("12,3", formatarKm(12.34))
    }
}
