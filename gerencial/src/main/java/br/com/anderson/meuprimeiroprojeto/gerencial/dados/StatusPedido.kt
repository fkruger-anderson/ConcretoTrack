package br.com.anderson.meuprimeiroprojeto.gerencial.dados

/**
 * O status do pedido não é gravado, é calculado a partir das viagens (ver
 * [br.com.anderson.meuprimeiroprojeto.gerencial.backend.PedidoRepositorio.calcularStatus]):
 * pendente (nada alocado ainda), em andamento (há saldo restante ou viagem atribuída/em rota
 * pendente), concluído (saldo zero e nenhuma viagem atribuída ou em rota).
 */
enum class StatusPedido {
    PENDENTE,
    EM_ANDAMENTO,
    CONCLUIDO
}
