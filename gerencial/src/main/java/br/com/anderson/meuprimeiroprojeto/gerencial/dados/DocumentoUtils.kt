package br.com.anderson.meuprimeiroprojeto.gerencial.dados

/**
 * Ferramentas para CPF e CNPJ: limpar, descobrir o tipo, validar e aplicar a máscara (os pontos,
 * a barra e o traço).
 *
 * Como saber se é CPF ou CNPJ? Pela quantidade de números: 11 dígitos = CPF (pessoa Física);
 * 14 dígitos = CNPJ (pessoa Jurídica).
 *
 * Como validar? Os dois últimos números de um CPF ou CNPJ são "dígitos verificadores": eles são
 * calculados a partir dos outros números por uma conta chamada "módulo 11". Se alguém digitar um
 * número errado, a conta não bate, e o documento é recusado. Isso não prova que o documento existe,
 * só que ele foi digitado sem erro.
 *
 * É um `object` com funções "puras" (só fazem contas com o que recebem), então é fácil de testar.
 */
object DocumentoUtils {

    // Os dois valores possíveis do tipo de pessoa, guardados como constantes para não haver erro
    // de digitação.
    const val FISICA = "FISICA"
    const val JURIDICA = "JURIDICA"

    /** Tira tudo o que não é número (pontos, traço, barra, espaços). "529.982.247-25" vira "52998224725". */
    fun apenasDigitos(texto: String): String = texto.filter { it.isDigit() }

    /**
     * Descobre o tipo de pessoa pela quantidade de dígitos: 11 = [FISICA], 14 = [JURIDICA]. Com
     * qualquer outra quantidade, devolve `null` (não é nem CPF nem CNPJ).
     */
    fun tipoPessoa(documentoComOuSemMascara: String): String? {
        val digitos = apenasDigitos(documentoComOuSemMascara)
        return when (digitos.length) {
            11 -> FISICA
            14 -> JURIDICA
            else -> null
        }
    }

    /** Diz se o documento é um CPF ou CNPJ válido, conferindo os dígitos verificadores. */
    fun documentoValido(documentoComOuSemMascara: String): Boolean {
        val digitos = apenasDigitos(documentoComOuSemMascara)
        return when (digitos.length) {
            11 -> cpfValido(digitos)
            14 -> cnpjValido(digitos)
            else -> false
        }
    }

    /**
     * Coloca a máscara enquanto o usuário digita, mesmo com o documento pela metade. Até 11 números
     * usa a máscara de CPF (000.000.000-00); acima disso, a de CNPJ (00.000.000/0000-00), cortando
     * o que passar de 14 números.
     */
    fun formatar(documentoComOuSemMascara: String): String {
        val d = apenasDigitos(documentoComOuSemMascara)
        return when {
            d.length <= 11 -> formatarCpfParcial(d)
            else -> formatarCnpjParcial(d.take(14))
        }
    }

    /**
     * Monta a máscara de CPF número por número: antes da 4ª e da 7ª posição coloca um ponto, e antes
     * da 10ª um traço. Para quando os números acabam. O `StringBuilder` é um jeito eficiente de ir
     * montando um texto aos pedaços.
     */
    private fun formatarCpfParcial(d: String): String {
        val sb = StringBuilder()
        for (i in d.indices) {
            when (i) {
                3, 6 -> sb.append('.')
                9 -> sb.append('-')
            }
            sb.append(d[i])
        }
        return sb.toString()
    }

    /** Mesma ideia do CPF, com as posições do CNPJ: pontos, uma barra e um traço. */
    private fun formatarCnpjParcial(d: String): String {
        val sb = StringBuilder()
        for (i in d.indices) {
            when (i) {
                2, 5 -> sb.append('.')
                8 -> sb.append('/')
                12 -> sb.append('-')
            }
            sb.append(d[i])
        }
        return sb.toString()
    }

    /**
     * Confere os dois dígitos verificadores do CPF. Recusa também CPFs com todos os números iguais
     * (como 111.111.111-11): a conta "bate" para eles, mas não são CPFs de verdade.
     *
     * O primeiro dígito é calculado com os 9 primeiros números; o segundo, com esses 9 mais o
     * primeiro dígito. `it - '0'` transforma o caractere '7' no número 7.
     */
    private fun cpfValido(cpf: String): Boolean {
        if (cpf.length != 11 || cpf.all { it == cpf[0] }) return false
        val digitos = cpf.map { it - '0' }
        val d1 = digitoVerificador(digitos.subList(0, 9), pesoInicial = 10)
        val d2 = digitoVerificador(digitos.subList(0, 9) + d1, pesoInicial = 11)
        return digitos[9] == d1 && digitos[10] == d2
    }

    /**
     * Confere os dois dígitos verificadores do CNPJ, usando os "pesos" (multiplicadores) fixos
     * definidos pela Receita Federal. Também recusa CNPJs com todos os números iguais.
     */
    private fun cnpjValido(cnpj: String): Boolean {
        if (cnpj.length != 14 || cnpj.all { it == cnpj[0] }) return false
        val digitos = cnpj.map { it - '0' }
        val pesos1 = listOf(5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)
        val pesos2 = listOf(6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)
        val d1 = digitoVerificadorComPesos(digitos.subList(0, 12), pesos1)
        val d2 = digitoVerificadorComPesos(digitos.subList(0, 12) + d1, pesos2)
        return digitos[12] == d1 && digitos[13] == d2
    }

    /**
     * A conta do dígito do CPF: multiplica cada número por um peso que começa em [pesoInicial] e
     * diminui de 1 em 1, soma tudo, e usa o resto da divisão por 11 para chegar no dígito (resto 10
     * vira 0). O `%` é o operador de "resto da divisão".
     */
    private fun digitoVerificador(digitos: List<Int>, pesoInicial: Int): Int {
        var soma = 0
        var peso = pesoInicial
        for (digito in digitos) {
            soma += digito * peso
            peso--
        }
        val resto = (soma * 10) % 11
        return if (resto == 10) 0 else resto
    }

    /**
     * A conta do dígito do CNPJ: igual à do CPF na ideia, mas com a lista de pesos fixa em vez de
     * diminuir de 1 em 1. Resto menor que 2 vira 0; senão, o dígito é 11 menos o resto.
     */
    private fun digitoVerificadorComPesos(digitos: List<Int>, pesos: List<Int>): Int {
        val soma = digitos.indices.sumOf { digitos[it] * pesos[it] }
        val resto = soma % 11
        return if (resto < 2) 0 else 11 - resto
    }
}
