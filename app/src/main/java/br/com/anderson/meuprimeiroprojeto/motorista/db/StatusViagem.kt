package br.com.anderson.meuprimeiroprojeto.motorista.db

/**
 * As situações possíveis de uma viagem.
 *
 * `enum class` é uma lista fechada de opções. No Firestore a situação é gravada como texto, usando
 * o nome da opção (`StatusViagem.EM_ROTA.name` é o texto "EM_ROTA").
 *
 * Quem cancela uma viagem é sempre o gerente, pelo app gerencial (`ViagemRepositorio.cancelar`).
 * Este app só "percebe" o cancelamento quando lê a viagem de novo.
 */
enum class StatusViagem {
    /** Criada pelo gerente, com os m³ já reservados do pedido, esperando o motorista sair. */
    ATRIBUIDA,
    /** O roteiro que contém esta viagem foi iniciado: o caminhão está a caminho. */
    EM_ROTA,
    /** O motorista marcou a entrega como feita. */
    CONCLUIDA,
    /** O gerente cancelou (informando um motivo); os m³ voltam para o saldo do pedido. */
    CANCELADA
}
