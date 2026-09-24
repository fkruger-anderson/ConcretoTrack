package br.com.anderson.meuprimeiroprojeto.motorista.backend

import com.google.firebase.firestore.IgnoreExtraProperties

/**
 * Um roteiro do jeito que está gravado no Firestore, no documento `roteiros/{id}`.
 *
 * Roteiro é uma saída do caminhão levando várias viagens de uma vez. Quem cria o roteiro é o
 * próprio motorista (ao colocar a primeira viagem nele), e é ele quem o conduz até o fim.
 *
 * Alguns campos são "denormalizados": são cópias de dados que já existem em outro lugar, como
 * `motoristaNome` e `caminhaoPlaca`. Isso é comum no Firestore: guardar a cópia evita buscar
 * outro documento só para mostrar um nome na tela. O preço é que a cópia pode ficar
 * desatualizada. Por isso a regra "todas as viagens do roteiro usam o mesmo caminhão" é
 * conferida olhando cada viagem, e nunca este campo (ver [RoteiroRepositorio.adicionarViagem]).
 *
 * O documento também guarda a última posição do caminhão (`ultimaLatitude`, `ultimaLongitude`,
 * `ultimoHorario`), gravada pelo `RastreamentoService` para o mapa do gerente. Este app grava esses
 * campos mas nunca os lê, por isso eles não estão aqui. `@IgnoreExtraProperties` avisa o
 * Firestore que isso é de propósito (sem a anotação, ele deixaria um aviso no Logcat).
 *
 * O `?` depois de um tipo (por exemplo `Long?`) quer dizer que o campo pode estar vazio (`null`).
 * As datas são guardadas como número de milissegundos desde 1º/01/1970, o jeito padrão dos
 * computadores guardarem um instante no tempo.
 */
@IgnoreExtraProperties
data class RoteiroRemota(
    /** Código (uid do Firebase Auth) do motorista dono do roteiro. */
    val motoristaUid: String = "",
    /** Cópia do nome do motorista, só para exibir. */
    val motoristaNome: String = "",
    /** Cópia da placa do caminhão, só para exibir. */
    val caminhaoPlaca: String = "",
    /** Situação do roteiro, gravada como texto: um dos nomes do enum `StatusRoteiro`. */
    val status: String = "",
    /** Quando o motorista iniciou o roteiro; null se ainda não iniciou. */
    val dataInicio: Long? = null,
    /** Quando o motorista encerrou o roteiro; null se ainda não encerrou. */
    val dataFim: Long? = null,
    /** Total de km rodados, calculado a partir do GPS ao encerrar; null até lá. */
    val kmTotalRota: Double? = null
)
