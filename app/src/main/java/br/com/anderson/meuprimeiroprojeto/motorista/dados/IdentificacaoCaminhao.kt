package br.com.anderson.meuprimeiroprojeto.motorista.dados

fun identificacaoCaminhao(numero: String, placa: String): String = numero.ifBlank { placa }
