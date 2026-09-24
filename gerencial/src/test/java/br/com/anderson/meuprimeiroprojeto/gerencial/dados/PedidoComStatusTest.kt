package br.com.anderson.meuprimeiroprojeto.gerencial.dados

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Testes do saldo mostrado na lista de pedidos ([PedidoComStatus.saldo]).
 *
 * No `assertEquals(esperado, obtido, 0.0)` com números quebrados, o terceiro valor é a diferença
 * aceita. Aqui é 0.0: o saldo tem que ser exatamente o esperado, sem "sobrinhas" de arredondamento.
 */
class PedidoComStatusTest {

    /** Monta um pedido só com o que importa para o saldo (total e reservado). */
    private fun pedido(total: Double, alocados: Double) =
        PedidoComStatus(1, "Obra", total, alocados, StatusPedido.EM_ANDAMENTO, 0)

    /**
     * Pedido de 9,1 m³ com 8 m³ reservados: sobra exatamente 1,1. Sem tratar o arredondamento, a
     * conta dava 1,0999999999999996.
     */
    @Test
    fun saldoDePedidoDe9v1ComOitoReservadosEh1v1() {
        assertEquals(1.1, pedido(9.1, 8.0).saldo, 0.0)
    }

    /** Pedido de 1 m³ entregue em 0,7 + 0,1 + 0,1 + 0,1: não sobra nada. */
    @Test
    fun saldoDePedidoEntregueEmParcelasQuebradasEhZero() {
        assertEquals(0.0, pedido(1.0, 0.7 + 0.1 + 0.1 + 0.1).saldo, 0.0)
    }
}
