package br.com.anderson.meuprimeiroprojeto.gerencial.dados

fun identificacaoCaminhao(numero: String, placa: String): String = numero.ifBlank { placa }

private val REGEX_NUMERO_CAMINHAO = Regex("^[0-9]{1,4}$")

fun normalizarNumeroCaminhao(digitado: String): String? {
    val numero = digitado.trim()
    return numero.takeIf { REGEX_NUMERO_CAMINHAO.matches(it) && it.any { digito -> digito != '0' } }
}
