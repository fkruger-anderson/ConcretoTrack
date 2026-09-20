package br.com.anderson.meuprimeiroprojeto.motorista.dados

/** Pedido cadastrado pelo gerente (app gerencial), lido do Firestore. */
data class Pedido(
    val id: Int,
    val destinatarioNome: String,
    val rua: String,
    val numero: String,
    val bairro: String,
    val cidade: String,
    val uf: String,
    val cep: String,
    val quantidadeTotalM3: Double,
    val distanciaDedicadaKm: Double
) {
    /** Endereço em uma linha, para mostrar na lista de viagens do motorista. */
    fun enderecoResumido(): String = "$rua, $numero - $bairro, $cidade/$uf"
}
