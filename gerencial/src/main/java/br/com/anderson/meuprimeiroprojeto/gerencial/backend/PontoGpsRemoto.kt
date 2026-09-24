package br.com.anderson.meuprimeiroprojeto.gerencial.backend

/**
 * Uma posição do caminhão, gravada pelo app do motorista durante um roteiro, no documento
 * `roteiros/{id}/pontosGps/{id}` (uma "subcoleção": uma gaveta de fichas dentro da ficha do
 * roteiro).
 *
 * Os pontos, em ordem de horário, formam o trajeto desenhado no mapa do detalhe do roteiro.
 * Os valores padrão existem porque o Firestore precisa criar o objeto vazio antes de preencher.
 */
data class PontoGpsRemoto(
    /** Coordenadas do caminhão. */
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    /** Quando a posição foi lida (milissegundos desde 1º/01/1970). */
    val horario: Long = 0
)
