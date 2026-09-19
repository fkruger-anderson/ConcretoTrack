package br.com.anderson.meuprimeiroprojeto.motorista.backend

import br.com.anderson.meuprimeiroprojeto.motorista.db.StatusViagem
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

/** As viagens agora são criadas pelo gerente (app gerencial); o motorista só as lista e inicia. */
object ViagemRepositorio {

    private val db get() = Firebase.firestore

    /** Viagens atribuídas a este motorista que ele ainda não iniciou. */
    suspend fun buscarAtribuidasDoMotorista(motoristaUid: String): List<Pair<String, ViagemRemota>> {
        val snapshot = db.collection("viagens")
            .whereEqualTo("motoristaUid", motoristaUid)
            .whereEqualTo("status", StatusViagem.ATRIBUIDA.name)
            .get()
            .aguardar()
        return snapshot.documents.mapNotNull { documento ->
            val remota = documento.toObject(ViagemRemota::class.java) ?: return@mapNotNull null
            documento.id to remota
        }.sortedBy { it.second.dataAtribuicao }
    }

    /**
     * O motorista escolheu a viagem e vai começar o trajeto: só muda o status, sem mexer no
     * saldo. Não é `suspend` de propósito — a gente NÃO espera o servidor confirmar antes de
     * deixar o motorista seguir em frente. A persistência offline do Firestore já guarda essa
     * escrita localmente e sincroniza sozinha quando a conexão voltar; se esperássemos aqui, o
     * motorista ficaria travado sem sinal, que é justamente quando ele mais precisa continuar.
     */
    fun iniciarTransito(viagemId: String) {
        db.collection("viagens").document(viagemId)
            .update(
                mapOf(
                    "status" to StatusViagem.EM_ROTA.name,
                    "dataInicio" to System.currentTimeMillis()
                )
            )
    }

    /** Mesmo raciocínio de [iniciarTransito]: finalizar não pode depender de ter sinal agora. */
    fun finalizarViagem(viagemId: String) {
        db.collection("viagens").document(viagemId)
            .update(
                mapOf(
                    "status" to StatusViagem.CONCLUIDA.name,
                    "dataFim" to System.currentTimeMillis()
                )
            )
    }

    suspend fun buscarPorId(viagemId: String): ViagemRemota? {
        val documento = db.collection("viagens").document(viagemId).get().aguardar()
        return documento.toObject(ViagemRemota::class.java)
    }

    /** Usado para voltar à tela de rastreamento se o motorista sair dela sem finalizar. */
    suspend fun buscarEmRotaDoMotorista(motoristaUid: String): Pair<String, ViagemRemota>? {
        val snapshot = db.collection("viagens")
            .whereEqualTo("motoristaUid", motoristaUid)
            .whereEqualTo("status", StatusViagem.EM_ROTA.name)
            .limit(1)
            .get()
            .aguardar()
        val documento = snapshot.documents.firstOrNull() ?: return null
        val remota = documento.toObject(ViagemRemota::class.java) ?: return null
        return documento.id to remota
    }
}
