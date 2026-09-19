package br.com.anderson.meuprimeiroprojeto.gerencial.dados

/**
 * Validação de CPF/CNPJ pelos dígitos verificadores (mod 11) e máscara de exibição.
 * Tipo de pessoa é derivado da quantidade de dígitos: 11 = Física (CPF), 14 = Jurídica (CNPJ).
 */
object DocumentoUtils {

    const val FISICA = "FISICA"
    const val JURIDICA = "JURIDICA"

    fun apenasDigitos(texto: String): String = texto.filter { it.isDigit() }

    fun tipoPessoa(documentoComOuSemMascara: String): String? {
        val digitos = apenasDigitos(documentoComOuSemMascara)
        return when (digitos.length) {
            11 -> FISICA
            14 -> JURIDICA
            else -> null
        }
    }

    fun documentoValido(documentoComOuSemMascara: String): Boolean {
        val digitos = apenasDigitos(documentoComOuSemMascara)
        return when (digitos.length) {
            11 -> cpfValido(digitos)
            14 -> cnpjValido(digitos)
            else -> false
        }
    }

    fun formatar(documentoComOuSemMascara: String): String {
        val d = apenasDigitos(documentoComOuSemMascara)
        return when {
            d.length <= 11 -> formatarCpfParcial(d)
            else -> formatarCnpjParcial(d.take(14))
        }
    }

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

    private fun cpfValido(cpf: String): Boolean {
        if (cpf.length != 11 || cpf.all { it == cpf[0] }) return false
        val digitos = cpf.map { it - '0' }
        val d1 = digitoVerificador(digitos.subList(0, 9), pesoInicial = 10)
        val d2 = digitoVerificador(digitos.subList(0, 9) + d1, pesoInicial = 11)
        return digitos[9] == d1 && digitos[10] == d2
    }

    private fun cnpjValido(cnpj: String): Boolean {
        if (cnpj.length != 14 || cnpj.all { it == cnpj[0] }) return false
        val digitos = cnpj.map { it - '0' }
        val pesos1 = listOf(5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)
        val pesos2 = listOf(6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)
        val d1 = digitoVerificadorComPesos(digitos.subList(0, 12), pesos1)
        val d2 = digitoVerificadorComPesos(digitos.subList(0, 12) + d1, pesos2)
        return digitos[12] == d1 && digitos[13] == d2
    }

    /** Usado pelo CPF: peso decrescente a partir de [pesoInicial]. */
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

    private fun digitoVerificadorComPesos(digitos: List<Int>, pesos: List<Int>): Int {
        val soma = digitos.indices.sumOf { digitos[it] * pesos[it] }
        val resto = soma % 11
        return if (resto < 2) 0 else 11 - resto
    }
}
