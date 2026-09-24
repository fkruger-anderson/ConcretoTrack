package br.com.anderson.meuprimeiroprojeto.gerencial.dados

import kotlin.math.roundToLong

/**
 * A conta do saldo de um pedido (quantos m³ ainda podem ser atribuídos a viagens), feita num lugar
 * só para todo o app gerencial usar a mesma regra.
 *
 * Por que não basta fazer `total - reservado`? O computador guarda números quebrados (`Double`) em
 * binário, e muitos números "redondos" para nós, como 0,1 ou 7,3, não têm representação exata em
 * binário — é parecido com 1/3 = 0,3333... em decimal. Por isso aparecem "sobrinhas" nas contas:
 * - 0,7 + 0,1 + 0,1 + 0,1 dá 0,9999999999999999 (e o pedido de 1 m³ nunca concluía);
 * - 9,1 - 8 dá 1,0999999999999996 (e atribuir o 1,1 que sobrou era recusado por "saldo insuficiente").
 *
 * A solução é arredondar para o litro (3 casas depois da vírgula, já que 1 m³ = 1000 litros), que é
 * mais precisão do que qualquer caminhão de concreto precisa. Assim essas sobrinhas somem.
 *
 * É um `object` com funções "puras" (só fazem contas com o que recebem), por isso é fácil de testar.
 */
object SaldoPedido {

    /**
     * Arredonda uma quantidade de m³ para o litro mais próximo.
     *
     * Como funciona: multiplica por 1000 (passa para litros), arredonda para o número inteiro mais
     * próximo (`roundToLong`) e divide por 1000 de volta. Exemplo: 0,9999999999999999 m³ vira
     * 999,9999999999999 litros, que arredonda para 1000 litros, que é exatamente 1 m³.
     */
    fun arredondar(m3: Double): Double = (m3 * 1000).roundToLong() / 1000.0

    /**
     * O saldo do pedido: o total menos o que já está reservado, arredondado para o litro. Um
     * resultado negativo minúsculo (por exemplo -0,00000000000000005) também vira 0.
     */
    fun calcular(quantidadeTotalM3: Double, m3Alocados: Double): Double =
        arredondar(quantidadeTotalM3 - m3Alocados)
}
