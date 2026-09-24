package br.com.anderson.meuprimeiroprojeto.motorista.backend

import com.google.firebase.firestore.IgnoreExtraProperties

/**
 * Uma viagem do jeito que está gravada no Firestore, no documento `viagens/{id}`.
 *
 * Viagem é um caminhão levando uma parte de um pedido. Quem cria a viagem é o gerente, no app
 * gerencial, ao "atribuir" (escolher pedido, motorista, caminhão e quantidade). O motorista só
 * vê as viagens dele, junta essas viagens num roteiro e marca cada uma como entregue.
 *
 * `motoristaNome` e `caminhaoPlaca` são cópias feitas na hora da atribuição (dados
 * "denormalizados"), para mostrar essas informações sem buscar outros documentos. É a mesma ideia
 * usada em [RoteiroRemota].
 *
 * O documento no Firestore tem mais campos do que esta classe (por exemplo, o motivo do
 * cancelamento, que só o gerente vê). `@IgnoreExtraProperties` avisa o Firestore que isso é de
 * propósito: sem essa anotação, ele deixaria um aviso no Logcat para cada campo que sobrou.
 *
 * O `?` depois de um tipo (por exemplo `Long?`) quer dizer que o campo pode estar vazio (`null`).
 * As datas são milissegundos desde 1º/01/1970.
 */
@IgnoreExtraProperties
data class ViagemRemota(
    /** Número do pedido ao qual esta viagem pertence. */
    val pedidoId: Int = 0,
    /** Código (uid) do motorista que vai fazer a viagem. */
    val motoristaUid: String = "",
    /** Cópia do nome do motorista, só para exibir. */
    val motoristaNome: String = "",
    /** Cópia da placa do caminhão. É por ela que se confere se o roteiro usa um caminhão só. */
    val caminhaoPlaca: String = "",
    /** Quantos m³ de concreto esta viagem leva. */
    val metrosCubicos: Double = 0.0,
    /** Situação da viagem, gravada como texto: um dos nomes do enum `StatusViagem`. */
    val status: String = "",
    /** Quando o gerente criou a viagem. Também serve para colocar as viagens em ordem. */
    val dataAtribuicao: Long = 0,
    /** Quando o roteiro desta viagem foi iniciado; null antes disso. */
    val dataInicio: Long? = null,
    /** Quando o motorista marcou a entrega; null antes disso. */
    val dataFim: Long? = null,
    /** Quando o gerente cancelou; null se a viagem não foi cancelada. */
    val dataCancelamento: Long? = null,
    /** Parte do km total do roteiro que coube a esta viagem (o "rateio"); preenchido ao encerrar. */
    val kmPercorridos: Double? = null,
    /** Id do roteiro em que a viagem foi colocada; null enquanto ela não está em nenhum. */
    val roteiroId: String? = null
)
