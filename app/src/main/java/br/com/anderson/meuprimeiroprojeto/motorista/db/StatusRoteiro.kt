package br.com.anderson.meuprimeiroprojeto.motorista.db

/** Situação de um roteiro, espelhando o campo `status` do documento em `roteiros/{id}` no Firestore. */
enum class StatusRoteiro {
    /** O motorista está montando o roteiro (adicionando/removendo viagens), ainda não saiu. */
    PLANEJANDO,
    /** O motorista iniciou o roteiro; GPS ativo, viagens podem ser marcadas como entregues. */
    EM_ROTA,
    /** Todas as viagens do roteiro foram resolvidas (entregues ou canceladas) e o motorista encerrou. */
    CONCLUIDO
}
