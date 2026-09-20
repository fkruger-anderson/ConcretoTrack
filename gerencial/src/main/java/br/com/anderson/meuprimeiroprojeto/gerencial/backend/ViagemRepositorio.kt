package br.com.anderson.meuprimeiroprojeto.gerencial.backend

import br.com.anderson.meuprimeiroprojeto.gerencial.dados.StatusViagem
import com.google.firebase.Firebase
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore

/** Atribuição, cancelamento e consulta de viagens — inclusive as escritas que mexem no saldo do pedido, sempre em transação. */
object ViagemRepositorio {

    private val db get() = Firebase.firestore

    /** Lançada ao tentar atribuir uma viagem com m³ maior que o saldo restante do pedido. */
    class SaldoInsuficienteException : Exception("m³ maior que o saldo disponível")
    /** Lançada ao tentar cancelar uma viagem que já está concluída ou cancelada. */
    class EstadoInvalidoException : Exception("Essa viagem não pode mais ser cancelada")

    /**
     * Cria a viagem já vinculada a um motorista e um caminhão, e reserva o saldo do pedido —
     * tudo numa transação, pra não estourar o saldo se o gerente atribuir mais de uma viagem
     * ao mesmo pedido rapidamente. A viagem nasce "atribuída"; quem muda pra "em rota" é o
     * próprio motorista, ao iniciar o trânsito no app dele.
     */
    suspend fun atribuir(
        pedidoId: Int,
        motoristaUid: String,
        motoristaNome: String,
        motoristaCpf: String,
        caminhaoPlaca: String,
        metrosCubicos: Double
    ): String {
        val pedidoRef = db.collection("pedidos").document(pedidoId.toString())
        val viagemRef = db.collection("viagens").document()

        db.runTransaction { transacao ->
            val pedidoSnapshot = transacao.get(pedidoRef)
            val total = pedidoSnapshot.getDouble("quantidadeTotalM3") ?: 0.0
            val alocados = pedidoSnapshot.getDouble("m3Alocados") ?: 0.0
            val saldo = total - alocados

            if (metrosCubicos > saldo) {
                throw SaldoInsuficienteException()
            }

            transacao.update(pedidoRef, "m3Alocados", alocados + metrosCubicos)
            transacao.set(
                viagemRef,
                ViagemRemota(
                    pedidoId = pedidoId,
                    motoristaUid = motoristaUid,
                    motoristaNome = motoristaNome,
                    motoristaCpf = motoristaCpf,
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
     * Cancela a viagem (só funciona se ainda estiver atribuída ou em rota) e devolve os m³
     * reservados ao saldo do pedido — tudo na mesma transação, para não deixar o saldo
     * inconsistente se o Firestore reordenar essa escrita com uma atribuição concorrente.
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

            transacao.update(pedidoRef, "m3Alocados", (alocados - metrosCubicos).coerceAtLeast(0.0))
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

    /** Busca uma viagem específica pelo id do documento — usado no painel de detalhes da tela de rota. */
    suspend fun buscarPorId(viagemId: String): ViagemRemota? {
        val documento = db.collection("viagens").document(viagemId).get().aguardar()
        return documento.toObject(ViagemRemota::class.java)
    }

    /** Histórico de viagens de um pedido, mais recentes primeiro — usado na tela de detalhe do pedido. */
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
     * Escuta em tempo real as viagens em rota agora — usado pelo mapa do gerente. Quem cancela
     * a escuta é o chamador, guardando o [ListenerRegistration] retornado e chamando `.remove()`
     * quando não precisar mais.
     */
    fun escutarViagensEmRota(aoAtualizar: (List<Pair<String, ViagemRemota>>) -> Unit): ListenerRegistration {
        return db.collection("viagens")
            .whereEqualTo("status", StatusViagem.EM_ROTA.name)
            .addSnapshotListener { snapshot, erro ->
                if (erro != null || snapshot == null) return@addSnapshotListener
                val viagens = snapshot.documents.mapNotNull { documento ->
                    val viagem = documento.toObject(ViagemRemota::class.java) ?: return@mapNotNull null
                    documento.id to viagem
                }
                aoAtualizar(viagens)
            }
    }

    /** Viagens de um roteiro específico — usado na tela de detalhe do roteiro (mapa + rateio). */
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
