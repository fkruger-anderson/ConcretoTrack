package br.com.anderson.meuprimeiroprojeto.gerencial.backend

/**
 * Os dados de um motorista guardados no Firestore, no documento `motoristas/{uid}`.
 *
 * É o mesmo formato que o app do motorista lê. Quem cria este documento é o gerente, ao
 * cadastrar o motorista (ver `MotoristaRepositorio.cadastrar`).
 *
 * Os valores padrão existem porque o Firestore precisa criar o objeto vazio antes de preencher.
 */
data class MotoristaPerfil(
    /** Nome completo do motorista. */
    val nome: String = "",
    /** CPF do motorista, só com números. */
    val cpf: String = ""
)
