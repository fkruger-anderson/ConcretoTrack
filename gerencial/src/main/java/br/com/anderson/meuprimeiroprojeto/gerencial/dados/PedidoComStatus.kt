package br.com.anderson.meuprimeiroprojeto.gerencial.dados

/**
 * Um pedido pronto para aparecer na lista da aba Pedidos, já com a situação calculada pelo
 * `PedidoRepositorio` (a situação não é gravada no banco, é calculada na hora).
 */
data class PedidoComStatus(
    /** Número do pedido. */
    val id: Int,
    /** Nome de quem vai receber o concreto. */
    val destinatarioNome: String,
    /** Quantidade total do pedido, em m³. */
    val quantidadeTotalM3: Double,
    /** Quantos m³ já estão reservados para viagens. */
    val m3Alocados: Double,
    /** Situação calculada: pendente, em andamento ou concluído. */
    val status: StatusPedido,
    /** Data combinada para a entrega (milissegundos desde 1º/01/1970). */
    val dataEntrega: Long
) {
    /**
     * Quantos m³ ainda podem ser atribuídos a viagens novas, calculado por [SaldoPedido] (que
     * arredonda para o litro). É uma propriedade "calculada": o `get()` faz a conta toda vez que
     * alguém lê o valor, em vez de guardá-lo.
     */
    val saldo: Double get() = SaldoPedido.calcular(quantidadeTotalM3, m3Alocados)
}
