package br.com.anderson.meuprimeiroprojeto.motorista.backend

import br.com.anderson.meuprimeiroprojeto.motorista.dados.Pedido
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

/**
 * Pedidos agora são cadastrados pelo gerente (app gerencial) e vivem no Firestore.
 * O app do motorista só precisa ler os dados pra mostrar destinatário/endereço nas
 * viagens atribuídas a ele.
 */
object PedidoRepositorio {

    private val db get() = Firebase.firestore

    /** Busca um pedido pelo id (número sequencial usado como id do documento no Firestore). */
    suspend fun buscarPorId(id: Int): Pedido? {
        val documento = db.collection("pedidos").document(id.toString()).get().aguardar()
        val remoto = documento.toObject(PedidoRemoto::class.java) ?: return null
        return Pedido(
            id = id,
            destinatarioNome = remoto.destinatarioNome,
            rua = remoto.rua,
            numero = remoto.numero,
            bairro = remoto.bairro,
            cidade = remoto.cidade,
            uf = remoto.uf,
            cep = remoto.cep,
            quantidadeTotalM3 = remoto.quantidadeTotalM3
        )
    }
}
