package br.com.anderson.meuprimeiroprojeto.motorista.dados

import br.com.anderson.meuprimeiroprojeto.motorista.backend.ViagemRemota

/** Uma viagem em aberto (atribuída ou em rota) deste motorista, já com os dados do pedido. */
data class ViagemAbertaComPedido(
    val viagemId: String,
    val viagem: ViagemRemota,
    val pedido: Pedido?
)
