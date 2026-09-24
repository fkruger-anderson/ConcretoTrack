package br.com.anderson.meuprimeiroprojeto.motorista.backend

import android.util.Log
import br.com.anderson.meuprimeiroprojeto.motorista.db.AppDatabase
import br.com.anderson.meuprimeiroprojeto.motorista.db.EntregaPendente
import br.com.anderson.meuprimeiroprojeto.motorista.db.StatusRoteiro
import br.com.anderson.meuprimeiroprojeto.motorista.db.StatusViagem
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.withTimeout

/**
 * Tudo o que o motorista faz com o roteiro dele, gravando no Firestore.
 *
 * Roteiro é uma saída do caminhão levando várias viagens de uma vez. A "vida" de um roteiro é:
 * 1. PLANEJANDO: o motorista vai colocando e tirando viagens ([adicionarViagem], [removerViagem]).
 * 2. EM_ROTA: o motorista saiu da usina ([iniciarRoteiro]) e vai marcando cada entrega
 *    ([marcarEntregue]).
 * 3. CONCLUIDO: todas as viagens foram resolvidas e o motorista encerrou ([encerrarRoteiro]); os
 *    km rodados são divididos entre os pedidos.
 *
 * Uma palavra sobre "transação": é um jeito de gravar em que o Firestore primeiro lê o documento,
 * deixa o código decidir o que fazer e só grava se ninguém mexeu no documento nesse meio-tempo
 * (se mexeram, ele repete tudo). Serve para evitar que duas pessoas, gravando ao mesmo tempo,
 * estraguem o trabalho uma da outra. O preço é que precisa de internet na hora.
 *
 * Aqui, na maioria das gravações só o próprio motorista mexe no roteiro dele, então não há
 * disputa e não é preciso transação. A exceção é [marcarEntregue]: o gerente pode cancelar a
 * viagem pelo app dele enquanto o motorista está sem sinal. Por isso essa gravação usa transação.
 *
 * É um `object`: só existe uma cópia dele no app, usada direto pelo nome.
 */
object RoteiroRepositorio {

    // "TAG" é a etiqueta que aparece no Logcat do Android Studio, para achar as mensagens desta classe.
    private const val TAG = "RoteiroRepositorio"

    // Quanto tempo (10 segundos) esperar a transação de "marcar entregue" antes de desistir e
    // guardar a confirmação na fila do celular para mandar depois.
    private const val TEMPO_LIMITE_MS = 10_000L

    private val db get() = Firebase.firestore

    /**
     * Erro lançado ao tentar colocar no roteiro uma viagem de um caminhão diferente do que o
     * roteiro já usa (todas as viagens de um roteiro precisam ser do mesmo caminhão).
     */
    class CaminhaoIncompativelException :
        Exception("Esta viagem usa um caminhão diferente do roteiro atual")

    /** Erro lançado ao tentar colocar ou tirar viagens de um roteiro que já saiu da usina. */
    class RoteiroJaIniciadoException :
        Exception("Não é possível alterar as viagens de um roteiro já iniciado")

    /**
     * Procura o roteiro "ativo" do motorista: um roteiro sendo montado (PLANEJANDO) ou já na rua
     * (EM_ROTA). Cada motorista só pode ter um desses por vez.
     *
     * Devolve um par (id do documento, dados do roteiro), ou `null` se o motorista não tem nenhum
     * roteiro ativo agora.
     */
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

    /** Busca um roteiro pelo id do documento. Devolve `null` se ele não existir. */
    suspend fun buscarPorId(roteiroId: String): RoteiroRemota? {
        val documento = db.collection("roteiros").document(roteiroId).get().aguardar()
        return documento.toObject(RoteiroRemota::class.java)
    }

    /**
     * Coloca a viagem no roteiro ativo do motorista e devolve o id desse roteiro.
     *
     * Passo a passo:
     * 1. Busca a viagem. Se ela não existir, para com erro.
     * 2. Procura o roteiro ativo do motorista. Se não houver, cria um novo, já PLANEJANDO.
     * 3. Se o roteiro já saiu da usina, para com [RoteiroJaIniciadoException].
     * 4. Se o roteiro já tem viagens de outro caminhão, para com [CaminhaoIncompativelException].
     * 5. Grava na viagem o id do roteiro (é isso que "liga" a viagem ao roteiro).
     *
     * Precisa de internet na hora, porque precisa ler o roteiro e as viagens antes de decidir.
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
     * Tira a viagem do roteiro (só é permitido enquanto o roteiro está PLANEJANDO).
     *
     * Se essa era a última viagem do roteiro, apaga também o documento do roteiro, para não ficar
     * um roteiro vazio "esquecido" no banco.
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
     * Inicia o roteiro: o roteiro e todas as viagens indicadas passam para EM_ROTA, com o horário
     * de agora como início.
     *
     * Usa um "lote" (batch): várias gravações empacotadas juntas, que acontecem todas ou nenhuma.
     * Repare que esta função não é `suspend` e não espera resposta: o Firestore guarda o lote no
     * próprio celular e envia sozinho quando houver internet. Assim o motorista consegue sair da
     * usina mesmo sem sinal.
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
     * Marca uma entrega como feita. Devolve `true` se o servidor confirmou na hora, ou `false` se
     * a confirmação ficou guardada no celular para ser enviada depois.
     *
     * Por que é diferente de [iniciarRoteiro]? Imagine: o motorista está sem sinal, o gerente
     * cancela a viagem no escritório (o que devolve os m³ ao saldo do pedido) e depois o motorista
     * marca "entregue". Se a gravação fosse "cega", quando o sinal voltasse ela apagaria o
     * cancelamento e o saldo do pedido ficaria errado. Por isso a gravação de verdade
     * ([confirmarEntregaNoServidor]) é uma transação que confere a situação antes.
     *
     * Transação precisa de internet. Então a função tenta por até 10 segundos (`withTimeout`); se
     * não conseguir, guarda a confirmação numa fila local (tabela `EntregaPendente`, no Room). O
     * `RastreamentoService` tenta reenviar essa fila a cada nova posição de GPS, até dar certo —
     * sem o motorista precisar fazer nada.
     *
     * A fila fica no banco local, que já foi preparado quando o app começou a rodar (ver
     * `AppDatabase`); por isso esta função não precisa receber um `Context`.
     */
    suspend fun marcarEntregue(viagemId: String): Boolean {
        val agora = System.currentTimeMillis()
        return try {
            withTimeout(TEMPO_LIMITE_MS) {
                confirmarEntregaNoServidor(viagemId, agora)
            }
            AppDatabase.obter().entregaPendenteDao().remover(viagemId)
            true
        } catch (erro: Exception) {
            Log.w(TAG, "Entrega da viagem $viagemId não confirmada agora; guardada na fila local", erro)
            AppDatabase.obter().entregaPendenteDao().inserir(EntregaPendente(viagemId, agora))
            false
        }
    }

    /**
     * A gravação da entrega no servidor, feita numa transação: só muda a viagem para CONCLUIDA se
     * ela ainda estiver EM_ROTA agora. Se não estiver mais (por exemplo, o gerente cancelou), não
     * faz nada, porque a viagem já foi resolvida por outro caminho.
     *
     * [dataFim] é o horário em que o motorista tocou em "entregue" — e não o horário em que a
     * gravação finalmente conseguiu chegar ao servidor.
     *
     * Chamada por [marcarEntregue] e pela fila de reenvio do `RastreamentoService`.
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
     * Encerra o roteiro e divide os km rodados entre as viagens entregues (o "rateio de km").
     *
     * Como a divisão funciona: cada viagem entregue ganha um "peso" = m³ da viagem × distância
     * dedicada do pedido (a distância da usina até a obra). Cada viagem recebe uma fatia do
     * [kmTotalRota] proporcional ao seu peso.
     *
     * Exemplo: o caminhão rodou 100 km com duas viagens. A leva 5 m³ para uma obra a 10 km
     * (peso 50); B leva 5 m³ para uma obra a 30 km (peso 150). Peso total = 200. A recebe
     * 100 × 50 / 200 = 25 km, e B recebe o que sobra: 75 km.
     *
     * Regras importantes:
     * - Viagens canceladas ficam de fora: não recebem km e não entram no peso total.
     * - A última viagem (pela ordem de atribuição) recebe "o que sobrou" em vez de fazer a conta.
     *   Contas com números quebrados no computador têm pequenos arredondamentos; assim a soma
     *   das fatias dá exatamente o [kmTotalRota].
     * - Se o peso total for zero (m³ ou distância zerados), ninguém recebe km e só fica um aviso
     *   no Logcat.
     *
     * Tudo (os km de cada viagem e o roteiro CONCLUIDO) é gravado num único lote. [viagens] e
     * [distanciasPorPedido] já chegam prontos de quem chamou (o `RoteiroViewModel`).
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
            // Triple = um "trio": guarda três coisas juntas (id, viagem, peso).
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
