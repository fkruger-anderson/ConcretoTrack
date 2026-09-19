package br.com.anderson.meuprimeiroprojeto.gerencial.dados

import br.com.anderson.meuprimeiroprojeto.gerencial.formatarM3

/** Pedido pronto para exibir na lista/RecyclerView, já com o [status] calculado pelo [br.com.anderson.meuprimeiroprojeto.gerencial.backend.PedidoRepositorio]. */
data class PedidoComStatus(
    val id: Int,
    val destinatarioNome: String,
    val quantidadeTotalM3: Double,
    val m3Alocados: Double,
    val status: StatusPedido,
    val dataEntrega: Long
) {
    /** m³ ainda disponíveis para atribuir a novas viagens. */
    val saldo: Double get() = quantidadeTotalM3 - m3Alocados

    /** Descrição legível para aparecer direto no spinner de seleção de pedido (tela de atribuir viagem). */
    override fun toString(): String =
        "Pedido nº $id — $destinatarioNome (restam ${formatarM3(saldo)} m³)"
}
