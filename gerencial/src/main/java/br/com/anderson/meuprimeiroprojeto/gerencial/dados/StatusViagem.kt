package br.com.anderson.meuprimeiroprojeto.gerencial.dados

/**
 * As situações possíveis de uma viagem. É uma cópia do enum do app do motorista: os dois apps
 * precisam concordar, porque o valor é gravado como texto no Firestore (por exemplo "ATRIBUIDA").
 *
 * `enum class` é uma lista fechada de opções.
 */
enum class StatusViagem {
    /** Criada pelo gerente, com os m³ reservados, esperando o motorista sair. */
    ATRIBUIDA,
    /** O roteiro com esta viagem saiu da usina. */
    EM_ROTA,
    /** O motorista marcou a entrega. */
    CONCLUIDA,
    /** O gerente cancelou; os m³ voltaram para o saldo do pedido. */
    CANCELADA
}
