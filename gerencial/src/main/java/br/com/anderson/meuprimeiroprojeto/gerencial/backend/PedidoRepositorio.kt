package br.com.anderson.meuprimeiroprojeto.gerencial.backend

import br.com.anderson.meuprimeiroprojeto.gerencial.dados.PedidoComStatus
import br.com.anderson.meuprimeiroprojeto.gerencial.dados.StatusPedido
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

object PedidoRepositorio {

    private val db get() = Firebase.firestore

    suspend fun listarComStatus(): List<PedidoComStatus> {
        val snapshot = db.collection("pedidos").get().aguardar()
        val pedidos = snapshot.documents.mapNotNull { documento ->
            val id = documento.id.toIntOrNull() ?: return@mapNotNull null
            val remoto = documento.toObject(PedidoRemoto::class.java) ?: return@mapNotNull null
            id to remoto
        }.sortedBy { it.first }

        return pedidos.map { (id, remoto) ->
            val saldo = remoto.quantidadeTotalM3 - remoto.m3Alocados
            val status = when {
                remoto.m3Alocados <= 0.0 -> StatusPedido.PENDENTE
                saldo > 0.0 -> StatusPedido.EM_ANDAMENTO
                temViagemPendente(id) -> StatusPedido.EM_ANDAMENTO
                else -> StatusPedido.CONCLUIDO
            }
            PedidoComStatus(id, remoto.destinatarioNome, remoto.quantidadeTotalM3, remoto.m3Alocados, status)
        }
    }

    suspend fun buscarPorId(id: Int): PedidoRemoto? {
        val documento = db.collection("pedidos").document(id.toString()).get().aguardar()
        return documento.toObject(PedidoRemoto::class.java)
    }

    /** Só é chamado quando o saldo já está zerado, pra decidir entre "em andamento" e "concluído". */
    private suspend fun temViagemPendente(pedidoId: Int): Boolean {
        val snapshot = db.collection("viagens")
            .whereEqualTo("pedidoId", pedidoId)
            .whereIn("status", listOf("ATRIBUIDA", "EM_ROTA"))
            .limit(1)
            .get()
            .aguardar()
        return !snapshot.isEmpty
    }

    /**
     * Cria o pedido com um número sequencial, usando um contador em `contadores/pedidos`
     * numa transação (evita dois gerentes cadastrando ao mesmo tempo e gerando o mesmo número).
     * Começa em 4 porque os pedidos 1-3 já existem, semeados pelo app do motorista (etapa 3).
     */
    suspend fun cadastrar(
        destinatarioNome: String,
        documento: String,
        tipoPessoa: String,
        rua: String,
        numero: String,
        bairro: String,
        cidade: String,
        uf: String,
        cep: String,
        quantidadeTotalM3: Double
    ): Int {
        val contadorRef = db.collection("contadores").document("pedidos")

        val novoId = db.runTransaction { transacao ->
            val snapshot = transacao.get(contadorRef)
            val atual = snapshot.getLong("proximo") ?: 3L
            val proximo = atual + 1

            transacao.set(contadorRef, mapOf("proximo" to proximo))
            transacao.set(
                db.collection("pedidos").document(proximo.toString()),
                PedidoRemoto(
                    destinatarioNome = destinatarioNome,
                    documento = documento,
                    tipoPessoa = tipoPessoa,
                    rua = rua,
                    numero = numero,
                    bairro = bairro,
                    cidade = cidade,
                    uf = uf,
                    cep = cep,
                    quantidadeTotalM3 = quantidadeTotalM3,
                    m3Alocados = 0.0
                )
            )
            proximo
        }.aguardar()

        return novoId.toInt()
    }
}
