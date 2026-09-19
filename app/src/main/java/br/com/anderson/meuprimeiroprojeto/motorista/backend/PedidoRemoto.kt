package br.com.anderson.meuprimeiroprojeto.motorista.backend

/**
 * Documento em `pedidos/{id}` no Firestore. `m3Alocados` é mantido atômico pela
 * transação de [ViagemRepositorio.iniciarViagem] — é dele que vem o saldo real.
 */
data class PedidoRemoto(
    val destinatarioNome: String = "",
    val rua: String = "",
    val numero: String = "",
    val bairro: String = "",
    val cidade: String = "",
    val uf: String = "",
    val cep: String = "",
    val quantidadeTotalM3: Double = 0.0,
    val m3Alocados: Double = 0.0
)
