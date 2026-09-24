package br.com.anderson.meuprimeiroprojeto.gerencial.dados

/**
 * A lista das 27 siglas de estados (UF) do Brasil. Serve para as sugestões do campo UF no cadastro
 * de pedido (um `AutoCompleteTextView`, que vai sugerindo enquanto o gerente digita) e para
 * conferir se a UF digitada existe.
 *
 * É um `object`: só existe uma cópia, usada direto pelo nome (`Uf.siglas`).
 */
object Uf {
    /** As siglas, em ordem alfabética do nome do estado. */
    val siglas = listOf(
        "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO",
        "MA", "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI",
        "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO"
    )
}
