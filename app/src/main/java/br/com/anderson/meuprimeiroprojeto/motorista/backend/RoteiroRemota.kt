package br.com.anderson.meuprimeiroprojeto.motorista.backend

/**
 * Documento em `roteiros/{id}` no Firestore. `motoristaNome` é denormalizado (copiado da viagem
 * que criou o roteiro) pelo mesmo motivo que `ViagemRemota` guarda `motoristaNome` além do uid:
 * evita uma consulta extra só pra exibir o nome. Sem campo de caminhão próprio — todas as viagens
 * de um roteiro usam o mesmo caminhão, validado na hora de adicionar (ver `RoteiroRepositorio`).
 */
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
