package br.com.anderson.meuprimeiroprojeto.motorista.db

/**
 * Situação de uma viagem, espelhando o campo `status` do documento no Firestore.
 * Cancelamento (com motivo) chega na etapa 6.
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
