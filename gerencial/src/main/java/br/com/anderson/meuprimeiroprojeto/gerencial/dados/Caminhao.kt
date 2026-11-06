package br.com.anderson.meuprimeiroprojeto.gerencial.dados

/**
 * Um caminhão da usina, na versão que as telas usam. A placa é o identificador: no Firestore, ela
 * é o nome do documento do caminhão.
 */
data class Caminhao(
    /** Placa, em letras maiúsculas e sem traço, por exemplo "ABC1D23". */
    val placa: String,
    val numero: String,
    /** Fabricante. */
    val marca: String,
    /** Modelo. */
    val modelo: String,
    /** Ano de fabricação. */
    val ano: Int
) {
    override fun toString(): String = "${identificacaoCaminhao(numero, placa)} - $marca $modelo ($ano)"
}
