package br.com.anderson.meuprimeiroprojeto.gerencial.backend

/** Documento em `motoristas/{uid}` no Firestore — mesmo formato que o app do motorista lê. */
data class MotoristaPerfil(
    val nome: String = "",
    val cpf: String = ""
)
