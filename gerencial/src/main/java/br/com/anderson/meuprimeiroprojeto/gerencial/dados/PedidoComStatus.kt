package br.com.anderson.meuprimeiroprojeto.gerencial.dados

import br.com.anderson.meuprimeiroprojeto.gerencial.formatarM3

data class PedidoComStatus(
    val id: Int,
    val destinatarioNome: String,
    val quantidadeTotalM3: Double,
    val m3Alocados: Double,
    val status: StatusPedido
) {
    val saldo: Double get() = quantidadeTotalM3 - m3Alocados

    override fun toString(): String =
        "Pedido nº $id — $destinatarioNome (restam ${formatarM3(saldo)} m³)"
}
