package br.com.anderson.meuprimeiroprojeto.gerencial.dados

/** Espelha o enum do app motorista — o valor é gravado como texto no Firestore. */
enum class StatusViagem {
    ATRIBUIDA,
    EM_ROTA,
    CONCLUIDA,
    CANCELADA
}
