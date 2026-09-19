package br.com.anderson.meuprimeiroprojeto.gerencial.backend

/** Documento em `viagens/{id}/pontosGps/{id}` no Firestore. */
data class PontoGpsRemoto(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val horario: Long = 0
)
