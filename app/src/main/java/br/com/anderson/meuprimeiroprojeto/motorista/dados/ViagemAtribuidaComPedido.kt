package br.com.anderson.meuprimeiroprojeto.motorista.dados

import br.com.anderson.meuprimeiroprojeto.motorista.backend.ViagemRemota

/** Uma viagem atribuída ao motorista, já com os dados do pedido pra exibir na lista. */
data class ViagemAtribuidaComPedido(
    val viagemId: String,
    val viagem: ViagemRemota,
    val pedido: Pedido?
)
