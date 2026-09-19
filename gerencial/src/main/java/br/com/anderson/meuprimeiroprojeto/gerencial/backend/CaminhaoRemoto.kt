package br.com.anderson.meuprimeiroprojeto.gerencial.backend

/** Documento em `caminhoes/{placa}` no Firestore — a placa é o próprio id do documento. */
data class CaminhaoRemoto(
    val marca: String = "",
    val modelo: String = "",
    val ano: Int = 0
)
