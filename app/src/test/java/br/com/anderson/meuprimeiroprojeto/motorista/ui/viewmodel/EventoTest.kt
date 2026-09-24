package br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Testes da "embalagem" de avisos de uma vez só ([Evento]) no app do motorista.
 *
 * É ela que impede, por exemplo, que o aviso "entrega enfileirada" apareça duas vezes quando o
 * celular gira logo depois de o motorista marcar uma entrega sem sinal.
 */
class EventoTest {

    /** A primeira leitura entrega o conteúdo. */
    @Test
    fun primeiraLeituraEntregaOConteudo() {
        val evento = Evento("entrega enfileirada")
        assertEquals("entrega enfileirada", evento.getConteudoSeNaoTratado())
    }

    /** Depois de lido uma vez, o evento não entrega mais nada. */
    @Test
    fun segundaLeituraNaoEntregaNada() {
        val evento = Evento("entrega enfileirada")
        evento.getConteudoSeNaoTratado()
        assertNull(evento.getConteudoSeNaoTratado())
    }
}
