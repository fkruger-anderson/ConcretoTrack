package br.com.anderson.meuprimeiroprojeto.motorista.backend

/**
 * Documento em `viagens/{id}` no Firestore — uma viagem (caminhão levando parte de um pedido),
 * criada pelo gerente ao atribuir. `motoristaNome`, `motoristaCpf` e `caminhaoPlaca` são
 * denormalizados (copiados na hora da atribuição, além do `motoristaUid`) pra exibir esses dados
 * sem precisar de uma consulta extra — mesma ideia usada em [RoteiroRemota].
 */
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
    val ultimaLongitude: Double? = null,
    val ultimoHorario: Long? = null,
    val kmPercorridos: Double? = null,
    val roteiroId: String? = null
)
