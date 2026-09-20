package br.com.anderson.meuprimeiroprojeto.motorista.backend

/**
 * Documento em `roteiros/{id}` no Firestore. `motoristaNome` é denormalizado (copiado da viagem
 * que criou o roteiro) pelo mesmo motivo que `ViagemRemota` guarda `motoristaNome` além do uid:
 * evita uma consulta extra só pra exibir o nome. `caminhaoPlaca` é denormalizado do mesmo jeito,
 * só pra exibição (ex.: marcador do mapa no gerencial) — a validação de "mesmo caminhão em todo
 * o roteiro" continua lendo `caminhaoPlaca` de cada `ViagemRemota` (ver `RoteiroRepositorio`),
 * nunca deste campo, que não é garantidamente consistente com uma viagem removida/cancelada.
 */
data class RoteiroRemota(
    val motoristaUid: String = "",
    val motoristaNome: String = "",
    val caminhaoPlaca: String = "",
    val status: String = "",
    val dataInicio: Long? = null,
    val dataFim: Long? = null,
    val kmTotalRota: Double? = null,
    val ultimaLatitude: Double? = null,
    val ultimaLongitude: Double? = null,
    val ultimoHorario: Long? = null
)
