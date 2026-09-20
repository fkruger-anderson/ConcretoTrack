package br.com.anderson.meuprimeiroprojeto.gerencial.dados

/** Espelha o enum do app motorista — o valor é gravado como texto no Firestore. */
enum class StatusRoteiro {
    PLANEJANDO,
    EM_ROTA,
    CONCLUIDO
}
