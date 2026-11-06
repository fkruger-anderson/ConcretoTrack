package br.com.anderson.meuprimeiroprojeto.motorista.dados

import org.junit.Assert.assertEquals
import org.junit.Test

class IdentificacaoCaminhaoTest {

    @Test
    fun usaONumeroQuandoExiste() {
        assertEquals("12", identificacaoCaminhao("12", "ABC1D23"))
    }

    @Test
    fun usaAPlacaQuandoNaoHaNumero() {
        assertEquals("ABC1D23", identificacaoCaminhao("", "ABC1D23"))
    }
}
