package br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Testes da "embalagem" de avisos de uma vez só ([Evento]).
 *
 * É ela que impede, por exemplo, que a tela feche de novo ou que um Toast apareça duas vezes
 * quando o celular gira logo depois de um cadastro dar certo.
 */
class EventoTest {

    /** A primeira leitura entrega o conteúdo. */
    @Test
    fun primeiraLeituraEntregaOConteudo() {
        val evento = Evento("pedido salvo")
        assertEquals("pedido salvo", evento.getConteudoSeNaoTratado())
    }

    /** Depois de lido uma vez, o evento não entrega mais nada. */
    @Test
    fun segundaLeituraNaoEntregaNada() {
        val evento = Evento("pedido salvo")
        evento.getConteudoSeNaoTratado()
        assertNull(evento.getConteudoSeNaoTratado())
    }
}
