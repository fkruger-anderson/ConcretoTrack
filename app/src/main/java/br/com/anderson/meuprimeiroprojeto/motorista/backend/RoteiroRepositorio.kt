package br.com.anderson.meuprimeiroprojeto.motorista.backend

import android.content.Context
import android.util.Log
import br.com.anderson.meuprimeiroprojeto.motorista.db.AppDatabase
import br.com.anderson.meuprimeiroprojeto.motorista.db.EntregaPendente
import br.com.anderson.meuprimeiroprojeto.motorista.db.StatusRoteiro
import br.com.anderson.meuprimeiroprojeto.motorista.db.StatusViagem
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.withTimeout

/**
 * Monta e conduz o roteiro do motorista: adicionar/remover viagem (enquanto planejando),
 * iniciar, marcar entrega e encerrar (com o cálculo do rateio de km). Só o próprio motorista
 * mexe no seu roteiro — sem concorrência entre motoristas diferentes, então as escritas aqui não
 * precisam de transação (diferente de "atribuir viagem" no gerencial, que pode ter dois gerentes
 * mexendo no saldo do mesmo pedido ao mesmo tempo).
 */
object RoteiroRepositorio {

    private const val TAG = "RoteiroRepositorio"

    // marcarEntregue precisa de conexão na hora (transação) — sem isso o motorista ficaria
    // esperando pra sempre, então cortamos e guardamos a confirmação na fila local.
    private const val TEMPO_LIMITE_MS = 10_000L

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

    /**
     * Marca uma entrega como concluída — diferente das outras escritas "cegas" deste objeto,
     * esta precisa ler o status atual primeiro: se o gerente cancelou essa viagem remotamente
     * enquanto o motorista estava offline, uma escrita cega poderia sobrescrever o cancelamento
     * de volta pra CONCLUIDA (corrompendo o saldo em m³ do pedido, que o cancelamento já
     * devolveu). Por isso usa uma transação, o que exige conexão na hora.
     *
     * Pra não perder a resiliência offline que o motorista precisa em campo, quando a transação
     * não consegue completar a tempo (sem sinal agora), a confirmação é guardada localmente
     * (fila em Room) e o [RastreamentoService][br.com.anderson.meuprimeiroprojeto.motorista.gps.RastreamentoService]
     * tenta reenviar automaticamente a cada atualização de localização enquanto o roteiro
     * estiver em rota — sem o motorista precisar fazer nada.
     *
     * Retorna true se confirmou agora mesmo no servidor, false se ficou na fila local pra
     * reenviar depois (não é um erro — é esperado e tratado).
     */
    suspend fun marcarEntregue(context: Context, viagemId: String): Boolean {
        val agora = System.currentTimeMillis()
        return try {
            withTimeout(TEMPO_LIMITE_MS) {
                confirmarEntregaNoServidor(viagemId, agora)
            }
            AppDatabase.obter(context).entregaPendenteDao().remover(viagemId)
            true
        } catch (erro: Exception) {
            AppDatabase.obter(context).entregaPendenteDao().inserir(EntregaPendente(viagemId, agora))
            false
        }
    }

    /**
     * A escrita em si: só grava CONCLUIDA se a viagem ainda estiver EM_ROTA no servidor agora —
     * se já não estiver mais (ex.: CANCELADA pelo gerente nesse meio-tempo), não faz nada, porque
     * a viagem já foi resolvida por outro caminho. Chamada tanto por [marcarEntregue] quanto pela
     * fila de retentativa do RastreamentoService.
     */
    suspend fun confirmarEntregaNoServidor(viagemId: String, dataFim: Long) {
        val viagemRef = db.collection("viagens").document(viagemId)
        db.runTransaction { transacao ->
            val snapshot = transacao.get(viagemRef)
            if (snapshot.getString("status") == StatusViagem.EM_ROTA.name) {
                transacao.update(
                    viagemRef,
                    mapOf(
                        "status" to StatusViagem.CONCLUIDA.name,
                        "dataFim" to dataFim
                    )
                )
            }
            null
        }.aguardar()
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
