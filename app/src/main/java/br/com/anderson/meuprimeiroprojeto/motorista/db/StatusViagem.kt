package br.com.anderson.meuprimeiroprojeto.motorista.db

/**
 * Situação de uma viagem, espelhando o campo `status` do documento no Firestore.
 * Quem cancela é o gerente, pelo app gerencial (`ViagemRepositorio.cancelar`) — o app do
 * motorista só lê o status CANCELADA quando isso acontece (ex.: enquanto está em rota).
 */
enum class StatusViagem {
    /** Criada pelo gerente, com m³ já reservado do saldo do pedido, aguardando o motorista iniciar. */
    ATRIBUIDA,
    /** Motorista iniciou o trânsito; GPS ativo. */
    EM_ROTA,
    /** Viagem finalizada pelo motorista no destino. */
    CONCLUIDA,
    /** Cancelada (com motivo); devolve os m³ reservados ao saldo do pedido. */
    CANCELADA
}
