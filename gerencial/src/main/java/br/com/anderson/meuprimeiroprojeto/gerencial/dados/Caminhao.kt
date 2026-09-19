package br.com.anderson.meuprimeiroprojeto.gerencial.dados

data class Caminhao(
    val placa: String,
    val marca: String,
    val modelo: String,
    val ano: Int
) {
    override fun toString(): String = "$placa - $marca $modelo ($ano)"
}
