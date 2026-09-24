package br.com.anderson.meuprimeiroprojeto.gerencial.dados

/**
 * As situações possíveis de um pedido.
 *
 * `enum class` é uma lista fechada de opções. Diferente da viagem e do roteiro, a situação do
 * pedido NÃO é gravada no banco: ela é calculada toda vez a partir das viagens (ver
 * `PedidoRepositorio.calcularStatus`), então nunca fica desatualizada.
 */
enum class StatusPedido {
    /** Nenhum m³ foi reservado para viagens ainda. */
    PENDENTE,
    /** Ainda sobra saldo, ou tudo já foi reservado mas há viagem atribuída ou em rota. */
    EM_ANDAMENTO,
    /** Saldo zero e nenhuma viagem atribuída ou em rota: tudo foi entregue (ou cancelado). */
    CONCLUIDO
}
