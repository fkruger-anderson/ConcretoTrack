package br.com.anderson.meuprimeiroprojeto.gerencial.dados

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun numeroPreservaZerosAEsquerda() {
        assertEquals("04", normalizarNumeroCaminhao("04"))
        assertEquals("007", normalizarNumeroCaminhao(" 007 "))
    }

    @Test
    fun numeroSoComZerosOuComLetraEhRecusado() {
        assertNull(normalizarNumeroCaminhao("00"))
        assertNull(normalizarNumeroCaminhao("4A"))
        assertNull(normalizarNumeroCaminhao(""))
        assertNull(normalizarNumeroCaminhao("12345"))
    }

    @Test
    fun spinnerMostraNumeroComZeroAEsquerda() {
        assertEquals("04 - Mercedes-Benz Atego 2426 (2020)", Caminhao("ABC1D23", "04", "Mercedes-Benz", "Atego 2426", 2020).toString())
    }

    @Test
    fun spinnerMostraONumeroEmVezDaPlaca() {
        assertEquals("12 - Mercedes-Benz Atego 2426 (2020)", Caminhao("ABC1D23", "12", "Mercedes-Benz", "Atego 2426", 2020).toString())
    }

    @Test
    fun spinnerMostraAPlacaDeCaminhaoAntigoSemNumero() {
        assertEquals("ABC1D23 - Mercedes-Benz Atego 2426 (2020)", Caminhao("ABC1D23", "", "Mercedes-Benz", "Atego 2426", 2020).toString())
    }
}
