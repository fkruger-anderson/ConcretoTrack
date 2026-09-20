package br.com.anderson.meuprimeiroprojeto.motorista

import java.util.Locale

/** Formata m³ sem casas decimais desnecessárias (20.0 -> "20", 6.5 -> "6,5"). */
fun formatarM3(valor: Double): String {
    return if (valor == valor.toLong().toDouble()) {
        valor.toLong().toString()
    } else {
        String.format(Locale.getDefault(), "%.1f", valor)
    }
}

/** Formata km com uma casa decimal (12.0 -> "12,0", 12.34 -> "12,3"). */
fun formatarKm(valor: Double): String {
    return String.format(Locale.getDefault(), "%.1f", valor)
}
