package br.com.anderson.meuprimeiroprojeto.gerencial.dados

/**
 * As situações possíveis de um roteiro. É uma cópia do enum do app do motorista: os dois apps
 * precisam concordar, porque o valor é gravado como texto no Firestore (por exemplo "EM_ROTA").
 *
 * `enum class` é uma lista fechada de opções.
 */
enum class StatusRoteiro {
    /** O motorista está montando o roteiro e ainda não saiu. */
    PLANEJANDO,
    /** O caminhão saiu: é o que aparece no mapa em tempo real. */
    EM_ROTA,
    /** O motorista encerrou o roteiro e os km foram divididos entre os pedidos. */
    CONCLUIDO
}
