package br.com.anderson.meuprimeiroprojeto.motorista.backend

import br.com.anderson.meuprimeiroprojeto.motorista.db.StatusViagem
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

/**
 * Busca viagens no Firestore para o app do motorista.
 *
 * Quem cria as viagens é o gerente, no app gerencial. Aqui só existem consultas (leituras): as
 * mudanças de situação de uma viagem (iniciar, entregar) ficam no [RoteiroRepositorio], porque
 * sempre acontecem através do roteiro.
 *
 * As funções devolvem `Pair<String, ViagemRemota>`: um "par" com duas coisas juntas — o id do
 * documento (que não faz parte dos dados da viagem) e os dados da viagem.
 */
object ViagemRepositorio {

    private val db get() = Firebase.firestore

    /**
     * Viagens do motorista que ainda podem entrar num roteiro: as que estão atribuídas a ele e
     * ainda não foram colocadas em nenhum roteiro. É o que aparece na aba "Viagens".
     *
     * A consulta ao Firestore pede as viagens do motorista com status ATRIBUIDA ou EM_ROTA; depois,
     * já no celular, o `filter` fica só com as que não têm roteiro. Uma viagem EM_ROTA sempre tem
     * roteiro (ela só entra em rota junto com o roteiro dela), então o filtro já a tira da lista.
     * No fim, o `sortedBy` coloca em ordem de atribuição (a mais antiga primeiro).
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

    /**
     * Todas as viagens que estão dentro de um roteiro, em ordem de atribuição. Usada pela aba
     * Roteiro (para listar as viagens) e na hora de encerrar o roteiro (para dividir os km).
     *
     * O `mapNotNull` transforma cada documento em um par (id, viagem) e descarta os que não
     * puderam ser lidos.
     */
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

    /** Busca uma viagem pelo id do documento. Devolve `null` se ela não existir. */
    suspend fun buscarPorId(viagemId: String): ViagemRemota? {
        val documento = db.collection("viagens").document(viagemId).get().aguardar()
        return documento.toObject(ViagemRemota::class.java)
    }
}
