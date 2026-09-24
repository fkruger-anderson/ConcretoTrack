package br.com.anderson.meuprimeiroprojeto.gerencial.backend

/**
 * Os dados do gerente guardados no Firestore, no documento `gerentes/{uid}` (o `uid` é o código
 * que o Firebase Auth dá para cada conta de login).
 *
 * O valor padrão existe porque o Firestore precisa criar o objeto vazio antes de preencher.
 */
data class GerentePerfil(
    /** Nome do gerente, mostrado na saudação da aba Configurações. */
    val nome: String = ""
)
