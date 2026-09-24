package br.com.anderson.meuprimeiroprojeto.gerencial.backend

import br.com.anderson.meuprimeiroprojeto.gerencial.dados.StatusRoteiro
import com.google.firebase.Firebase
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore

/**
 * Consulta os roteiros no Firestore. O gerencial só LÊ roteiros: quem cria e mexe neles é sempre
 * o motorista, pelo app dele.
 *
 * É um `object`: só existe uma cópia, usada direto pelo nome.
 */
object RoteiroRepositorio {

    private val db get() = Firebase.firestore

    /** Busca um roteiro pelo id; devolve `null` se não existir. Usado no detalhe do roteiro. */
    suspend fun buscarPorId(roteiroId: String): RoteiroRemota? {
        val documento = db.collection("roteiros").document(roteiroId).get().aguardar()
        return documento.toObject(RoteiroRemota::class.java)
    }

    /**
     * Fica "escutando" os roteiros que estão em rota agora. É o que faz o mapa do gerente se
     * atualizar sozinho.
     *
     * Diferente de uma busca normal (que pergunta uma vez e recebe a resposta), o
     * `addSnapshotListener` deixa um "ouvinte" ligado: toda vez que algum roteiro em rota muda no
     * Firestore (por exemplo, o caminhão andou), a função [aoAtualizar] é chamada de novo com a
     * lista atualizada.
     *
     * O ouvinte fica ligado até alguém desligar. Por isso a função devolve um
     * `ListenerRegistration` (o "controle remoto" do ouvinte): quem chamou guarda esse objeto e
     * chama `.remove()` quando não precisar mais.
     */
    fun escutarRoteirosEmRota(aoAtualizar: (List<Pair<String, RoteiroRemota>>) -> Unit): ListenerRegistration {
        return db.collection("roteiros")
            .whereEqualTo("status", StatusRoteiro.EM_ROTA.name)
            .addSnapshotListener { snapshot, erro ->
                if (erro != null || snapshot == null) return@addSnapshotListener
                val roteiros = snapshot.documents.mapNotNull { documento ->
                    val roteiro = documento.toObject(RoteiroRemota::class.java) ?: return@mapNotNull null
                    documento.id to roteiro
                }
                aoAtualizar(roteiros)
            }
    }

    /**
     * Busca os pontos de GPS gravados durante o roteiro (na subcoleção `pontosGps` do documento
     * do roteiro) e os coloca em ordem de horário, para desenhar o trajeto no mapa.
     */
    suspend fun listarPontosGps(roteiroId: String): List<PontoGpsRemoto> {
        val snapshot = db.collection("roteiros").document(roteiroId)
            .collection("pontosGps")
            .get()
            .aguardar()
        return snapshot.documents
            .mapNotNull { it.toObject(PontoGpsRemoto::class.java) }
            .sortedBy { it.horario }
    }
}
