package br.com.anderson.meuprimeiroprojeto.gerencial.backend

/**
 * Documento em `pedidos/{id}` no Firestore. `documento`/`tipoPessoa` só aparecem aqui e no
 * cadastro (LGPD) — o app do motorista nem lê esses campos.
 */
data class PedidoRemoto(
    val destinatarioNome: String = "",
    val documento: String = "",
    val tipoPessoa: String = "",
    val rua: String = "",
    val numero: String = "",
    val bairro: String = "",
    val cidade: String = "",
    val uf: String = "",
    val cep: String = "",
    val quantidadeTotalM3: Double = 0.0,
    val m3Alocados: Double = 0.0,
    val dataEntrega: Long = 0
)
