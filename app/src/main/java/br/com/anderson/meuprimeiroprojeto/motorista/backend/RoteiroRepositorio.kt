package br.com.anderson.meuprimeiroprojeto.motorista.backend

import android.util.Log
import br.com.anderson.meuprimeiroprojeto.motorista.db.StatusRoteiro
import br.com.anderson.meuprimeiroprojeto.motorista.db.StatusViagem
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

/**
 * Monta e conduz o roteiro do motorista: adicionar/remover viagem (enquanto planejando),
 * iniciar, marcar entrega e encerrar (com o cálculo do rateio de km). Só o próprio motorista
 * mexe no seu roteiro — sem concorrência entre motoristas diferentes, então as escritas aqui não
 * precisam de transação (diferente de "atribuir viagem" no gerencial, que pode ter dois gerentes
 * mexendo no saldo do mesmo pedido ao mesmo tempo).
 */
object RoteiroRepositorio {

    private const val TAG = "RoteiroRepositorio"

    private val db get() = Firebase.firestore

    /** Lançada ao tentar adicionar uma viagem com caminhão diferente das já presentes no roteiro. */
    class CaminhaoIncompativelException :
        Exception("Esta viagem usa um caminhão diferente do roteiro atual")

    /** Lançada ao tentar adicionar/remover viagem de um roteiro que já foi iniciado. */
    class RoteiroJaIniciadoException :
        Exception("Não é possível alterar as viagens de um roteiro já iniciado")

    /** Busca o roteiro ativo do motorista (planejando ou em rota) — só pode haver um por vez. */
    suspend fun buscarRoteiroAtivo(motoristaUid: String): Pair<String, RoteiroRemota>? {
        val snapshot = db.collection("roteiros")
            .whereEqualTo("motoristaUid", motoristaUid)
            .whereIn("status", listOf(StatusRoteiro.PLANEJANDO.name, StatusRoteiro.EM_ROTA.name))
            .get()
            .aguardar()
        val documento = snapshot.documents.firstOrNull() ?: return null
        val remoto = documento.toObject(RoteiroRemota::class.java) ?: return null
        return documento.id to remoto
    }

    /** Busca um roteiro específico pelo id do documento. */
    suspend fun buscarPorId(roteiroId: String): RoteiroRemota? {
        val documento = db.collection("roteiros").document(roteiroId).get().aguardar()
        return documento.toObject(RoteiroRemota::class.java)
    }

    /**
     * Adiciona a viagem ao roteiro ativo do motorista, criando um roteiro novo (PLANEJANDO) se
     * ele ainda não tiver nenhum. Precisa de conexão na hora: lê o roteiro/as viagens já nele
     * antes de decidir (valida caminhão consistente), diferente das escritas "cegas" como
     * iniciar/marcar entregue. Retorna o id do roteiro (novo ou existente).
     */
    suspend fun adicionarViagem(viagemId: String, motoristaUid: String): String {
        val viagem = ViagemRepositorio.buscarPorId(viagemId)
            ?: throw IllegalStateException("Viagem não encontrada")

        val roteiroExistente = buscarRoteiroAtivo(motoristaUid)
        val (roteiroId, roteiro) = if (roteiroExistente != null) {
            roteiroExistente
        } else {
            val novoRef = db.collection("roteiros").document()
            val novoRoteiro = RoteiroRemota(
                motoristaUid = motoristaUid,
                motoristaNome = viagem.motoristaNome,
                caminhaoPlaca = viagem.caminhaoPlaca,
                status = StatusRoteiro.PLANEJANDO.name
            )
            novoRef.set(novoRoteiro).aguardar()
            novoRef.id to novoRoteiro
        }

        if (roteiro.status != StatusRoteiro.PLANEJANDO.name) throw RoteiroJaIniciadoException()

        val viagensNoRoteiro = ViagemRepositorio.buscarPorRoteiro(roteiroId)
        val caminhaoDoRoteiro = viagensNoRoteiro.firstOrNull()?.second?.caminhaoPlaca
        if (caminhaoDoRoteiro != null && caminhaoDoRoteiro != viagem.caminhaoPlaca) {
            throw CaminhaoIncompativelException()
        }

        db.collection("viagens").document(viagemId).update("roteiroId", roteiroId).aguardar()
        return roteiroId
    }

    /**
     * Remove a viagem do roteiro (só funciona enquanto ele está PLANEJANDO). Se essa era a
     * última viagem do roteiro, apaga o próprio documento do roteiro (evita roteiro vazio órfão).
     */
    suspend fun removerViagem(viagemId: String, roteiroId: String) {
        val roteiro = buscarPorId(roteiroId)
        if (roteiro?.status != StatusRoteiro.PLANEJANDO.name) throw RoteiroJaIniciadoException()

        val viagensNoRoteiro = ViagemRepositorio.buscarPorRoteiro(roteiroId)
        db.collection("viagens").document(viagemId).update("roteiroId", null).aguardar()

        if (viagensNoRoteiro.size <= 1) {
            db.collection("roteiros").document(roteiroId).delete().aguardar()
        }
    }

    /**
     * Inicia o roteiro: muda o status dele e de todas as suas viagens pra EM_ROTA de uma vez,
     * numa escrita em lote (WriteBatch). Não espera confirmação do servidor — mesmo raciocínio
     * de sempre, funciona offline.
     */
    fun iniciarRoteiro(roteiroId: String, viagemIds: List<String>) {
        val agora = System.currentTimeMillis()
        val lote = db.batch()
        lote.update(
            db.collection("roteiros").document(roteiroId),
            mapOf("status" to StatusRoteiro.EM_ROTA.name, "dataInicio" to agora)
        )
        for (viagemId in viagemIds) {
            lote.update(
                db.collection("viagens").document(viagemId),
                mapOf("status" to StatusViagem.EM_ROTA.name, "dataInicio" to agora)
            )
        }
        lote.commit()
    }

    /** Marca uma entrega como concluída. Escrita direta, sem esperar confirmação do servidor. */
    fun marcarEntregue(viagemId: String) {
        db.collection("viagens").document(viagemId)
            .update(
                mapOf(
                    "status" to StatusViagem.CONCLUIDA.name,
                    "dataFim" to System.currentTimeMillis()
                )
            )
    }

    /**
     * Encerra o roteiro: rateia o [kmTotalRota] entre as viagens CONCLUIDA por peso
     * (volume × distância dedicada do pedido) — canceladas ficam de fora do rateio (não entram
     * no pesoTotal). A última viagem (por `dataAtribuicao`, ordem estável) recebe o resto da divisão, garantindo
     * que a soma dos km atribuídos seja exatamente igual a [kmTotalRota] mesmo com arredondamento
     * de ponto flutuante. [viagens] e [distanciasPorPedido] já vêm prontos de quem chamou (a
     * ViewModel, que tem acesso ao Room pra calcular [kmTotalRota] via Haversine).
     */
    suspend fun encerrarRoteiro(
        roteiroId: String,
        kmTotalRota: Double,
        viagens: List<Pair<String, ViagemRemota>>,
        distanciasPorPedido: Map<Int, Double>
    ) {
        val concluidas = viagens
            .filter { (_, viagem) -> viagem.status == StatusViagem.CONCLUIDA.name }
            .sortedBy { (_, viagem) -> viagem.dataAtribuicao }

        val lote = db.batch()

        if (concluidas.isNotEmpty()) {
            val pesos = concluidas.map { (id, viagem) ->
                val distancia = distanciasPorPedido[viagem.pedidoId] ?: 0.0
                Triple(id, viagem, viagem.metrosCubicos * distancia)
            }
            val pesoTotal = pesos.sumOf { it.third }

            if (pesoTotal > 0.0) {
                var somaParcial = 0.0
                for (i in pesos.indices) {
                    val (viagemId, _, peso) = pesos[i]
                    val kmAtribuido = if (i == pesos.lastIndex) {
                        kmTotalRota - somaParcial
                    } else {
                        (kmTotalRota * (peso / pesoTotal)).also { somaParcial += it }
                    }
                    lote.update(db.collection("viagens").document(viagemId), "kmPercorridos", kmAtribuido)
                }
            } else {
                Log.w(
                    TAG,
                    "Roteiro $roteiroId: pesoTotal <= 0 (volume ou distância dedicada zerados) — " +
                        "nenhuma viagem recebeu kmPercorridos no rateio"
                )
            }
        }

        lote.update(
            db.collection("roteiros").document(roteiroId),
            mapOf(
                "status" to StatusRoteiro.CONCLUIDO.name,
                "dataFim" to System.currentTimeMillis(),
                "kmTotalRota" to kmTotalRota
            )
        )
        lote.commit().aguardar()
    }
}
