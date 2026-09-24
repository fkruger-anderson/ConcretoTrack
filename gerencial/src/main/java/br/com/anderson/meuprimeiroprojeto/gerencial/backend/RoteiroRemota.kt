package br.com.anderson.meuprimeiroprojeto.gerencial.backend

/**
 * Um roteiro do jeito que está gravado no Firestore, no documento `roteiros/{id}`.
 *
 * Roteiro é uma saída do caminhão levando várias viagens de uma vez. Quem cria e conduz o roteiro
 * é o motorista (no app dele); o gerencial só lê.
 *
 * `motoristaNome` e `caminhaoPlaca` são cópias ("denormalizadas") só para exibir, sem precisar
 * buscar outro documento. Nunca são usadas para conferir se o caminhão é o mesmo em todo o roteiro.
 *
 * O `?` depois de um tipo quer dizer que o campo pode estar vazio (`null`). As datas são
 * milissegundos desde 1º/01/1970.
 */
data class RoteiroRemota(
    /** Código (uid) do motorista dono do roteiro. */
    val motoristaUid: String = "",
    /** Cópia do nome do motorista, para exibir. */
    val motoristaNome: String = "",
    /** Cópia da placa do caminhão, para exibir. */
    val caminhaoPlaca: String = "",
    /** Situação do roteiro, gravada como texto (nome de um `StatusRoteiro`). */
    val status: String = "",
    /** Quando o motorista saiu; null se ainda não saiu. */
    val dataInicio: Long? = null,
    /** Quando o motorista encerrou; null se ainda não encerrou. */
    val dataFim: Long? = null,
    /** Total de km rodados, calculado a partir do GPS quando o roteiro é encerrado. */
    val kmTotalRota: Double? = null,
    /** Última posição conhecida do caminhão, atualizada a cada 10 s: é o que o mapa mostra. */
    val ultimaLatitude: Double? = null,
    val ultimaLongitude: Double? = null,
    /** Horário da última posição. Hoje nenhuma tela deste app lê este campo. */
    val ultimoHorario: Long? = null
)
