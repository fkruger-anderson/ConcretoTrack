package br.com.anderson.meuprimeiroprojeto.motorista.backend

/** Documento em `motoristas/{uid}` no Firestore. */
data class MotoristaPerfil(
    val nome: String = "",
    val cpf: String = ""
)
