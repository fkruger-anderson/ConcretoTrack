package br.com.anderson.meuprimeiroprojeto.gerencial.backend

import br.com.anderson.meuprimeiroprojeto.gerencial.dados.StatusRoteiro
import com.google.firebase.Firebase
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore

/** Consulta de roteiros — o gerencial só lê (criar/editar roteiro é sempre feito pelo motorista). */
object RoteiroRepositorio {

    private val db get() = Firebase.firestore

    /** Busca um roteiro específico pelo id do documento — usado na tela de detalhe do roteiro. */
    suspend fun buscarPorId(roteiroId: String): RoteiroRemota? {
        val documento = db.collection("roteiros").document(roteiroId).get().aguardar()
        return documento.toObject(RoteiroRemota::class.java)
    }

    /**
     * Escuta em tempo real os roteiros em rota agora — usado pelo mapa do gerente. Quem cancela
     * a escuta é o chamador, guardando o [ListenerRegistration] retornado e chamando `.remove()`
     * quando não precisar mais.
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

    /** Pontos de GPS gravados durante o roteiro, em ordem, pra desenhar o trajeto no mapa. */
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
