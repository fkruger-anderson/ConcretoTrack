package br.com.anderson.meuprimeiroprojeto.motorista

import java.util.Locale

// Este arquivo não tem classe: só duas funções "soltas" (chamadas de top-level), que qualquer
// parte do app pode usar direto, como `formatarM3(6.5)`. Servem para transformar um número em
// texto bonito para a tela.

/**
 * Transforma uma quantidade de metros cúbicos em texto, sem casas decimais desnecessárias.
 *
 * Exemplos: 20.0 vira "20" (e não "20,0"); 6.5 vira "6,5".
 *
 * Como funciona: `valor.toLong()` joga fora a parte depois da vírgula (6.5 vira 6). Se, voltando
 * para `Double`, o número continua igual ao original, é porque ele não tinha parte decimal, e
 * então é mostrado sem vírgula. Senão, é mostrado com uma casa decimal ("%.1f").
 *
 * `Locale.getDefault()` usa o idioma do celular para escolher o separador: vírgula em português,
 * ponto em inglês.
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
 *
 * Exemplos: 12.0 vira "12,0"; 12.34 vira "12,3". O "%.1f" quer dizer "número com 1 casa depois
 * da vírgula".
 */
fun formatarKm(valor: Double): String {
    return String.format(Locale.getDefault(), "%.1f", valor)
}
