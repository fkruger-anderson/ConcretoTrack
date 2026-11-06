package br.com.anderson.meuprimeiroprojeto.gerencial.backend

data class EnderecoCep(
    val logradouro: String? = null,
    val bairro: String? = null,
    val localidade: String? = null,
    val erro: Boolean? = null
)
