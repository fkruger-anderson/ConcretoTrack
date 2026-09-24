package br.com.anderson.meuprimeiroprojeto.gerencial.backend

import br.com.anderson.meuprimeiroprojeto.gerencial.dados.StatusPedido
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Testes da regra que decide a situação de um pedido ([PedidoRepositorio.calcularStatus]).
 *
 * `calcularStatus` é uma "função pura": só faz contas com o que recebe, sem internet nem banco.
 * Por isso dá para testá-la aqui, direto no computador, sem Firebase.
 *
 * Cada função marcada com `@Test` é um teste separado. O `assertEquals(esperado, obtido)` confere
 * se o resultado é o esperado; se não for, o teste fica vermelho e mostra a diferença.
 */
class PedidoRepositorioTest {

    /** Nenhum m³ reservado ainda: o pedido está pendente. */
    @Test
    fun pedidoSemNadaReservadoEhPendente() {
        assertEquals(StatusPedido.PENDENTE, PedidoRepositorio.calcularStatus(0.0, 10.0, false))
    }

    /** Ainda sobra saldo: o pedido está em andamento, mesmo sem viagem na rua agora. */
    @Test
    fun pedidoComSaldoSobrandoEhEmAndamento() {
        assertEquals(StatusPedido.EM_ANDAMENTO, PedidoRepositorio.calcularStatus(4.0, 10.0, false))
    }

    /** Tudo reservado, mas ainda há viagem para entregar: continua em andamento. */
    @Test
    fun pedidoTodoReservadoComViagemPendenteEhEmAndamento() {
        assertEquals(StatusPedido.EM_ANDAMENTO, PedidoRepositorio.calcularStatus(10.0, 10.0, true))
    }

    /** Tudo reservado e nenhuma viagem pendente: o pedido está concluído. */
    @Test
    fun pedidoTodoReservadoSemViagemPendenteEhConcluido() {
        assertEquals(StatusPedido.CONCLUIDO, PedidoRepositorio.calcularStatus(10.0, 10.0, false))
    }

    /**
     * Bug do arredondamento: somar 0,7 + 0,1 + 0,1 + 0,1 no computador dá 0,9999999999999999, e
     * não 1. Sem tratar isso, sobra um "saldo fantasma" e o pedido de 1 m³ nunca conclui.
     *
     * A soma é feita aqui do mesmo jeito que o Firestore acumula os m³, uma viagem de cada vez.
     */
    @Test
    fun pedidoEntregueEmParcelasQuebradasConclui() {
        val alocados = 0.7 + 0.1 + 0.1 + 0.1
        assertEquals(StatusPedido.CONCLUIDO, PedidoRepositorio.calcularStatus(alocados, 1.0, false))
    }

    /**
     * Mesmo bug, no caminho contrário: reservar 0,1 e 0,2 e depois cancelar as duas viagens deixa
     * 0,00000000000000003 "reservado", e o pedido aparecia como em andamento em vez de pendente.
     */
    @Test
    fun pedidoComTodasAsViagensCanceladasVoltaASerPendente() {
        val alocados = 0.1 + 0.2 - 0.1 - 0.2
        assertEquals(StatusPedido.PENDENTE, PedidoRepositorio.calcularStatus(alocados, 1.0, false))
    }
}
