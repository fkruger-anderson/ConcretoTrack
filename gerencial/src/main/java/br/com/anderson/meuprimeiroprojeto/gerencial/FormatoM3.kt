package br.com.anderson.meuprimeiroprojeto.gerencial

import java.util.Locale

// Este arquivo não tem classe: só duas funções "soltas" (top-level) que qualquer tela do app pode
// usar direto, como `formatarM3(6.5)`. O app do motorista tem uma cópia igual deste arquivo.

/**
 * Transforma uma quantidade de metros cúbicos em texto, sem casas decimais desnecessárias.
 *
 * Exemplos: 20.0 vira "20" (e não "20,0"); 6.5 vira "6,5".
 *
 * Como funciona: `valor.toLong()` joga fora a parte depois da vírgula. Se, voltando para `Double`,
 * o número continua igual, é porque ele não tinha parte decimal, e aparece sem vírgula. Senão,
 * aparece com uma casa decimal ("%.1f"). `Locale.getDefault()` usa o idioma do celular para
 * escolher entre vírgula e ponto.
 */
fun formatarM3(valor: Double): String {
    return if (valor == valor.toLong().toDouble()) {
        valor.toLong().toString()
    } else {
        String.format(Locale.getDefault(), "%.1f", valor)
    }
}

/**
 * Transforma uma distância em km em texto, sempre com uma casa decimal.
 * Exemplos: 12.0 vira "12,0"; 12.34 vira "12,3".
 */
fun formatarKm(valor: Double): String {
    return String.format(Locale.getDefault(), "%.1f", valor)
}
