package br.com.anderson.meuprimeiroprojeto.gerencial.dados

/**
 * O status do pedido não é gravado, é calculado a partir das viagens:
 * pendente (nada alocado), em andamento (há saldo ou viagem em rota),
 * concluído (saldo zero e nenhuma viagem em rota).
 */
enum class StatusPedido {
    PENDENTE,
    EM_ANDAMENTO,
    CONCLUIDO
}
