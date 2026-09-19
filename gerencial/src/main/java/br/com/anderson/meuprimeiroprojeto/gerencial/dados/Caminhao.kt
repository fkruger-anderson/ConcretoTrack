package br.com.anderson.meuprimeiroprojeto.gerencial.dados

/** Caminhão cadastrado pelo gerente; a placa é o identificador (id do documento no Firestore). */
data class Caminhao(
    val placa: String,
    val marca: String,
    val modelo: String,
    val ano: Int
) {
    /** Descrição legível para aparecer direto no spinner de seleção de caminhão. */
    override fun toString(): String = "$placa - $marca $modelo ($ano)"
}
