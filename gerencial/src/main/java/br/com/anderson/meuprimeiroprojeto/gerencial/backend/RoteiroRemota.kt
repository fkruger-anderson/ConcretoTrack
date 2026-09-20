package br.com.anderson.meuprimeiroprojeto.gerencial.backend

/** Espelha o documento em `roteiros/{id}` no Firestore (ver o app motorista para o raciocínio dos campos). */
data class RoteiroRemota(
    val motoristaUid: String = "",
    val motoristaNome: String = "",
    val status: String = "",
    val dataInicio: Long? = null,
    val dataFim: Long? = null,
    val kmTotalRota: Double? = null,
    val ultimaLatitude: Double? = null,
    val ultimaLongitude: Double? = null,
    val ultimoHorario: Long? = null
)
