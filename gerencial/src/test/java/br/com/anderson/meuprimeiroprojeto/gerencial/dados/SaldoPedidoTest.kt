package br.com.anderson.meuprimeiroprojeto.gerencial.dados

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Testes da conta do saldo de um pedido ([SaldoPedido]).
 *
 * É essa conta que decide se o gerente pode atribuir uma viagem: a atribuição é recusada quando
 * os m³ pedidos são maiores que o saldo. Se o saldo sair um pouquinho menor que o real por causa
 * de arredondamento, o gerente não consegue atribuir exatamente o que sobrou.
 *
 * No `assertEquals(esperado, obtido, 0.0)`, o terceiro valor é a diferença aceita: 0.0 quer dizer
 * "tem que ser exatamente igual".
 */
class SaldoPedidoTest {

    /**
     * Pedido de 9,1 m³ com 8 m³ reservados: o saldo tem que ser exatamente 1,1, para o gerente
     * conseguir atribuir 1,1 m³. A conta direta dava 1,0999999999999996 e a atribuição era recusada.
     */
    @Test
    fun pedidoDe9v1ComOitoReservadosTemSaldo1v1() {
        assertEquals(1.1, SaldoPedido.calcular(9.1, 8.0), 0.0)
    }

    /** Mesmo problema com outros números: 7,3 - 7 dava 0,2999999999999998 em vez de 0,3. */
    @Test
    fun pedidoDe7v3ComSeteReservadosTemSaldo0v3() {
        assertEquals(0.3, SaldoPedido.calcular(7.3, 7.0), 0.0)
    }

    /** Pedido de 1 m³ entregue em 0,7 + 0,1 + 0,1 + 0,1 não pode ficar com "saldo fantasma". */
    @Test
    fun pedidoEntregueEmParcelasQuebradasFicaSemSaldo() {
        assertEquals(0.0, SaldoPedido.calcular(1.0, 0.7 + 0.1 + 0.1 + 0.1), 0.0)
    }

    /**
     * Pedido de 0,3 m³ entregue em 0,1 + 0,2 (que no computador dá 0,30000000000000004): o saldo
     * é zero, e não um número negativo minúsculo (que apareceria na tela como "-0,0").
     */
    @Test
    fun pedidoReservadoPorSomaQuebradaNaoFicaComSaldoNegativo() {
        assertEquals(0.0, SaldoPedido.calcular(0.3, 0.1 + 0.2), 0.0)
    }

    /**
     * Quantidades com casas de litro (até 3 casas depois da vírgula) não podem ser "comidas" pelo
     * arredondamento: um pedido de 8,125 m³ sem nada reservado tem saldo 8,125.
     */
    @Test
    fun quantidadeComCasasDeLitroNaoEhPerdida() {
        assertEquals(8.125, SaldoPedido.calcular(8.125, 0.0), 0.0)
    }

    /**
     * O arredondamento também serve para gravar os m³ reservados sem "sobrinhas": 0,7 + 0,1 + 0,1 +
     * 0,1 vira exatamente 1, e o que sobra de cancelar tudo (0,00000000000000003) vira 0.
     */
    @Test
    fun arredondarLimpaAsSobrasDeArredondamento() {
        assertEquals(1.0, SaldoPedido.arredondar(0.7 + 0.1 + 0.1 + 0.1), 0.0)
        assertEquals(0.0, SaldoPedido.arredondar(0.1 + 0.2 - 0.1 - 0.2), 0.0)
    }
}
