package br.com.anderson.meuprimeiroprojeto.motorista.backend

import br.com.anderson.meuprimeiroprojeto.motorista.db.StatusViagem
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

/** As viagens agora são criadas pelo gerente (app gerencial); o motorista só as lista e inicia. */
object ViagemRepositorio {

    private val db get() = Firebase.firestore

    /**
     * Viagens do motorista ainda disponíveis pra montar um roteiro: atribuídas e ainda sem
     * roteiro. Uma viagem em rota sempre tem `roteiroId` preenchido (só entra em rota junto com
     * o roteiro dela), então o filtro abaixo já a exclui automaticamente.
     */
    suspend fun buscarAbertasDoMotorista(motoristaUid: String): List<Pair<String, ViagemRemota>> {
        val snapshot = db.collection("viagens")
            .whereEqualTo("motoristaUid", motoristaUid)
            .whereIn("status", listOf(StatusViagem.ATRIBUIDA.name, StatusViagem.EM_ROTA.name))
            .get()
            .aguardar()
        return snapshot.documents.mapNotNull { documento ->
            val remota = documento.toObject(ViagemRemota::class.java) ?: return@mapNotNull null
            documento.id to remota
        }.filter { (_, viagem) -> viagem.roteiroId == null }
            .sortedBy { it.second.dataAtribuicao }
    }

    /** Viagens de um roteiro específico — usado pela aba Roteiro e ao encerrar o roteiro. */
    suspend fun buscarPorRoteiro(roteiroId: String): List<Pair<String, ViagemRemota>> {
        val snapshot = db.collection("viagens")
            .whereEqualTo("roteiroId", roteiroId)
            .get()
            .aguardar()
        return snapshot.documents.mapNotNull { documento ->
            val remota = documento.toObject(ViagemRemota::class.java) ?: return@mapNotNull null
            documento.id to remota
        }.sortedBy { it.second.dataAtribuicao }
    }

    /** Busca os dados de uma viagem específica pelo id do documento. */
    suspend fun buscarPorId(viagemId: String): ViagemRemota? {
        val documento = db.collection("viagens").document(viagemId).get().aguardar()
        return documento.toObject(ViagemRemota::class.java)
    }
}
