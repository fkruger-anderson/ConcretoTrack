package br.com.anderson.meuprimeiroprojeto.gerencial.dados

/**
 * Um caminhão da usina, na versão que as telas usam. A placa é o identificador: no Firestore, ela
 * é o nome do documento do caminhão.
 */
data class Caminhao(
    /** Placa, em letras maiúsculas e sem traço, por exemplo "ABC1D23". */
    val placa: String,
    /** Fabricante. */
    val marca: String,
    /** Modelo. */
    val modelo: String,
    /** Ano de fabricação. */
    val ano: Int
) {
    /**
     * O texto que representa o caminhão. A caixa de seleção (spinner) usa o `toString()` de cada
     * item para mostrar na lista, então aparece algo como "ABC1D23 - Mercedes Atego (2020)".
     */
    override fun toString(): String = "$placa - $marca $modelo ($ano)"
}
