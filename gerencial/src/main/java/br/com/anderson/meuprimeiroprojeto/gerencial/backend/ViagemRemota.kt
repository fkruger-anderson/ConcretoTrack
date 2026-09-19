package br.com.anderson.meuprimeiroprojeto.gerencial.backend

/** Espelha o documento em `viagens/{id}` no Firestore. */
data class ViagemRemota(
    val pedidoId: Int = 0,
    val motoristaUid: String = "",
    val motoristaNome: String = "",
    val motoristaCpf: String = "",
    val caminhaoPlaca: String = "",
    val metrosCubicos: Double = 0.0,
    val status: String = "",
    val dataAtribuicao: Long = 0,
    val dataInicio: Long? = null,
    val dataFim: Long? = null,
    val motivoCancelamento: String? = null,
    val dataCancelamento: Long? = null,
    val ultimaLatitude: Double? = null,
    val ultimaLongitude: Double? = null
)
