package br.com.anderson.meuprimeiroprojeto.motorista.db

/**
 * As situações possíveis de um roteiro.
 *
 * `enum class` é uma lista fechada de opções: um roteiro só pode estar em uma destas três, e o
 * compilador reclama se o código tentar usar um valor que não existe. No Firestore, a situação é
 * gravada como texto, usando o nome da opção (`StatusRoteiro.EM_ROTA.name` é o texto "EM_ROTA").
 */
enum class StatusRoteiro {
    /** O motorista está montando o roteiro (colocando e tirando viagens) e ainda não saiu. */
    PLANEJANDO,
    /** O motorista saiu da usina: o GPS está ligado e as viagens podem ser marcadas como entregues. */
    EM_ROTA,
    /** Todas as viagens foram resolvidas (entregues ou canceladas) e o motorista encerrou. */
    CONCLUIDO
}
