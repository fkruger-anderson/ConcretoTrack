package br.com.anderson.meuprimeiroprojeto.gerencial.backend

import br.com.anderson.meuprimeiroprojeto.gerencial.dados.SaldoPedido
import br.com.anderson.meuprimeiroprojeto.gerencial.dados.StatusViagem
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

/**
 * Atribui, cancela e consulta viagens no Firestore (coleção `viagens`).
 *
 * As duas gravações daqui mexem no saldo do pedido (os m³ reservados) e por isso sempre usam
 * transação. Transação é um jeito de gravar em que o Firestore lê os documentos, deixa o código
 * decidir e só grava se ninguém mexeu neles nesse meio-tempo; se mexeram, ele repete tudo. Isso
 * impede, por exemplo, que duas atribuições feitas ao mesmo tempo reservem mais concreto do que
 * o pedido tem. Transação precisa de internet na hora.
 *
 * É um `object`: só existe uma cópia, usada direto pelo nome.
 */
object ViagemRepositorio {

    private val db get() = Firebase.firestore

    /** Erro lançado ao tentar atribuir mais m³ do que o saldo que sobra no pedido. */
    class SaldoInsuficienteException : Exception("m³ maior que o saldo disponível")
    /** Erro lançado ao tentar cancelar uma viagem que já foi concluída ou cancelada. */
    class EstadoInvalidoException : Exception("Essa viagem não pode mais ser cancelada")

    /**
     * Cria uma viagem nova, ligada a um motorista e a um caminhão, e reserva os m³ dela no pedido.
     * Devolve o id da viagem criada.
     *
     * Dentro da transação:
     * 1. lê o pedido e calcula o saldo (total - já reservado) com [SaldoPedido], que arredonda para
     *    o litro — sem isso, atribuir exatamente o que sobrou (por exemplo 1,1 de um pedido de 9,1
     *    com 8 reservados) era recusado;
     * 2. se a quantidade pedida for maior que o saldo, lança [SaldoInsuficienteException] e nada é
     *    gravado;
     * 3. soma a quantidade em `m3Alocados` do pedido (já arredondada, para não acumular
     *    "sobrinhas" no banco) e cria o documento da viagem, já ATRIBUIDA.
     *
     * Quem muda a viagem para "em rota" depois é o motorista, ao iniciar o roteiro no app dele.
     */
    suspend fun atribuir(
        pedidoId: Int,
        motoristaUid: String,
        motoristaNome: String,
        caminhaoPlaca: String,
        metrosCubicos: Double
    ): String {
        val pedidoRef = db.collection("pedidos").document(pedidoId.toString())
        // `document()` sem nome cria uma referência com um id novo e aleatório para a viagem.
        val viagemRef = db.collection("viagens").document()

        db.runTransaction { transacao ->
            val pedidoSnapshot = transacao.get(pedidoRef)
            val total = pedidoSnapshot.getDouble("quantidadeTotalM3") ?: 0.0
            val alocados = pedidoSnapshot.getDouble("m3Alocados") ?: 0.0
            val saldo = SaldoPedido.calcular(total, alocados)

            if (metrosCubicos > saldo) {
                throw SaldoInsuficienteException()
            }

            transacao.update(pedidoRef, "m3Alocados", SaldoPedido.arredondar(alocados + metrosCubicos))
            transacao.set(
                viagemRef,
                ViagemRemota(
                    pedidoId = pedidoId,
                    motoristaUid = motoristaUid,
                    motoristaNome = motoristaNome,
                    caminhaoPlaca = caminhaoPlaca,
                    metrosCubicos = metrosCubicos,
                    status = StatusViagem.ATRIBUIDA.name,
                    dataAtribuicao = System.currentTimeMillis()
                )
            )
            null
        }.aguardar()

        return viagemRef.id
    }

    /**
     * Cancela uma viagem e devolve os m³ dela para o saldo do pedido.
     *
     * Dentro da transação:
     * 1. lê a viagem; se ela não estiver ATRIBUIDA nem EM_ROTA (já foi entregue ou cancelada),
     *    lança [EstadoInvalidoException];
     * 2. lê o pedido e subtrai os m³ da viagem de `m3Alocados`, arredondando para o litro (ver
     *    [SaldoPedido]) e sem nunca deixar negativo (`coerceAtLeast(0.0)`);
     * 3. marca a viagem como CANCELADA, com o motivo e o horário.
     *
     * Por ser uma transação, o cancelamento e a devolução do saldo acontecem juntos, ou nenhum dos
     * dois acontece.
     */
    suspend fun cancelar(viagemId: String, motivo: String) {
        val viagemRef = db.collection("viagens").document(viagemId)

        db.runTransaction { transacao ->
            val viagemSnapshot = transacao.get(viagemRef)
            val status = viagemSnapshot.getString("status")
            if (status != StatusViagem.ATRIBUIDA.name && status != StatusViagem.EM_ROTA.name) {
                throw EstadoInvalidoException()
            }
            val pedidoId = viagemSnapshot.getLong("pedidoId") ?: 0
            val metrosCubicos = viagemSnapshot.getDouble("metrosCubicos") ?: 0.0

            val pedidoRef = db.collection("pedidos").document(pedidoId.toString())
            val pedidoSnapshot = transacao.get(pedidoRef)
            val alocados = pedidoSnapshot.getDouble("m3Alocados") ?: 0.0

            transacao.update(pedidoRef, "m3Alocados", SaldoPedido.arredondar(alocados - metrosCubicos).coerceAtLeast(0.0))
            transacao.update(
                viagemRef,
                mapOf(
                    "status" to StatusViagem.CANCELADA.name,
                    "motivoCancelamento" to motivo,
                    "dataCancelamento" to System.currentTimeMillis()
                )
            )
            null
        }.aguardar()
    }

    /**
     * Todas as viagens de um pedido, da mais recente para a mais antiga (`sortedByDescending`).
     * Usada no histórico do pedido. Cada item é um par (id da viagem, dados da viagem).
     */
    suspend fun listarPorPedido(pedidoId: Int): List<Pair<String, ViagemRemota>> {
        val snapshot = db.collection("viagens")
            .whereEqualTo("pedidoId", pedidoId)
            .get()
            .aguardar()
        return snapshot.documents
            .mapNotNull { documento ->
                val remota = documento.toObject(ViagemRemota::class.java) ?: return@mapNotNull null
                documento.id to remota
            }
            .sortedByDescending { it.second.dataAtribuicao }
    }

    /**
     * Todas as viagens de um roteiro, em ordem de atribuição. Usada no detalhe do roteiro (mapa e
     * tabela de divisão dos km).
     */
    suspend fun listarPorRoteiro(roteiroId: String): List<Pair<String, ViagemRemota>> {
        val snapshot = db.collection("viagens")
            .whereEqualTo("roteiroId", roteiroId)
            .get()
            .aguardar()
        return snapshot.documents
            .mapNotNull { documento ->
                val remota = documento.toObject(ViagemRemota::class.java) ?: return@mapNotNull null
                documento.id to remota
            }
            .sortedBy { it.second.dataAtribuicao }
    }
}
