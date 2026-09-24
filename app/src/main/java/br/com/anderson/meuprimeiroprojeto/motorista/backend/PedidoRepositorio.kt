package br.com.anderson.meuprimeiroprojeto.motorista.backend

import br.com.anderson.meuprimeiroprojeto.motorista.dados.Pedido
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

/**
 * Busca pedidos no Firestore para o app do motorista.
 *
 * Quem cadastra os pedidos é o gerente, no app gerencial. O motorista só precisa ler alguns
 * dados (nome do destinatário, endereço, distância) para mostrar junto das viagens dele.
 *
 * É um `object`: só existe uma cópia, chamada direto pelo nome (`PedidoRepositorio.buscarPorId(3)`).
 */
object PedidoRepositorio {

    private val db get() = Firebase.firestore

    /**
     * Busca o pedido de número [id] e devolve já convertido para [Pedido], a versão simplificada
     * que as telas usam. Devolve `null` se o pedido não existir.
     *
     * No Firestore, o nome de cada documento de pedido é o próprio número em texto ("1", "2"...),
     * por isso o `id.toString()`.
     *
     * É `suspend`: espera a resposta da internet sem travar a tela.
     */
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
            quantidadeTotalM3 = remoto.quantidadeTotalM3,
            distanciaDedicadaKm = remoto.distanciaDedicadaKm
        )
    }
}
