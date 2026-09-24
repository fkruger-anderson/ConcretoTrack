package br.com.anderson.meuprimeiroprojeto.gerencial.backend

import com.google.firebase.firestore.IgnoreExtraProperties

/**
 * Uma viagem do jeito que está gravada no Firestore, no documento `viagens/{id}`.
 *
 * Viagem é um caminhão levando uma parte de um pedido. É criada pelo gerente, ao atribuir (ver
 * `ViagemRepositorio.atribuir`). `motoristaNome` e `caminhaoPlaca` são cópias feitas nessa hora,
 * para mostrar esses dados sem buscar outros documentos.
 *
 * Esta classe serve para ler e também para gravar: ao atribuir, o repositório cria um objeto
 * destes e o Firestore o transforma em documento.
 *
 * Viagens antigas ainda guardam campos que não existem mais aqui (a cópia do CPF do motorista e
 * uma posição de GPS da época em que o rastreamento era por viagem). `@IgnoreExtraProperties`
 * avisa o Firestore para ignorar esses campos ao ler, em vez de deixar um aviso no Logcat.
 *
 * O `?` depois de um tipo quer dizer que o campo pode estar vazio (`null`). As datas são
 * milissegundos desde 1º/01/1970.
 */
@IgnoreExtraProperties
data class ViagemRemota(
    /** Número do pedido ao qual a viagem pertence. */
    val pedidoId: Int = 0,
    /** Código (uid) do motorista que vai levar. */
    val motoristaUid: String = "",
    /** Cópia do nome do motorista, para exibir. */
    val motoristaNome: String = "",
    /** Cópia da placa do caminhão. */
    val caminhaoPlaca: String = "",
    /** Quantos m³ esta viagem leva. */
    val metrosCubicos: Double = 0.0,
    /** Situação da viagem, gravada como texto (nome de um `StatusViagem`). */
    val status: String = "",
    /** Quando o gerente atribuiu a viagem. */
    val dataAtribuicao: Long = 0,
    /** Quando o roteiro da viagem saiu; null antes disso. */
    val dataInicio: Long? = null,
    /** Quando o motorista marcou a entrega; null antes disso. */
    val dataFim: Long? = null,
    /** O motivo que o gerente escreveu ao cancelar; null se não foi cancelada. */
    val motivoCancelamento: String? = null,
    /** Quando foi cancelada; null se não foi. */
    val dataCancelamento: Long? = null,
    /** Parte do km total do roteiro que coube a esta viagem; preenchido quando o roteiro é encerrado. */
    val kmPercorridos: Double? = null,
    /** Roteiro em que o motorista colocou esta viagem; null enquanto não está em nenhum. */
    val roteiroId: String? = null
)
