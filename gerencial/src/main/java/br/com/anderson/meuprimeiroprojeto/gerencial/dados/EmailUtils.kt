package br.com.anderson.meuprimeiroprojeto.gerencial.dados

/**
 * Confere se um texto tem formato de email (por exemplo "ana@usina.com.br").
 *
 * Por que não usar o `android.util.Patterns.EMAIL_ADDRESS`, que o Android já oferece? Porque ele
 * só funciona dentro do celular: nos testes que rodam no computador ele fica vazio (`null`) e o app
 * quebra. Esta cópia usa exatamente a mesma regra do Android, mas funciona nos dois lugares — então
 * os cadastros de motorista e de gerente podem ser testados.
 *
 * Isso só confere o FORMATO; se o email existe de verdade, só o Firebase descobre ao cadastrar.
 *
 * É um `object` com uma função "pura" (só confere o que recebe), fácil de testar.
 */
object EmailUtils {

    // A "expressão regular" (um molde de texto) usada pelo Android. Lendo por partes:
    // - antes do @: de 1 a 256 letras, números ou os símbolos + . _ % -
    // - depois do @: um nome de domínio (letra ou número, depois letras, números ou -)
    // - e pelo menos um pedaço ".algo" no fim (como ".com" ou ".com.br").
    private val FORMATO_EMAIL = Regex(
        "[a-zA-Z0-9+._%\\-]{1,256}" +
            "@" +
            "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +
            "(\\.[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25})+"
    )

    /** `true` se o texto inteiro tem formato de email (`matches` exige que o molde cubra tudo). */
    fun valido(email: String): Boolean = FORMATO_EMAIL.matches(email)
}
