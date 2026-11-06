package br.com.anderson.meuprimeiroprojeto.motorista.ui

import android.content.res.ColorStateList
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import br.com.anderson.meuprimeiroprojeto.R

/**
 * Mostra o "carregando" dentro de um botão, no lugar do texto, enquanto um processo está em
 * andamento (salvar, entrar, atribuir...). Usado pelos botões de formulário de todas as telas.
 *
 * Enquanto [carregando] for `true`:
 * - o texto do botão some (fica guardado numa "etiqueta" do próprio botão, para voltar depois) e a
 *   rodinha [progresso], que fica em cima do botão no layout, aparece;
 * - o botão fica desabilitado (não aceita novo toque), mas mantém a cor de habilitado, para não
 *   parecer que "apagou".
 *
 * Quando termina, o texto, a cor original e a descrição para leitores de tela voltam.
 *
 * [habilitado] é para botões que também ficam desligados por outro motivo (por exemplo, "Iniciar
 * roteiro" sem viagens): nesse caso o botão fica cinza, como qualquer botão desabilitado.
 *
 * É uma "função de extensão": é chamada como se fosse um método do próprio botão,
 * `botaoSalvar.exibirCarregando(estado.carregando, progresso)`.
 */
fun Button.exibirCarregando(
    carregando: Boolean,
    progresso: ProgressBar,
    habilitado: Boolean = true,
    descricaoCarregando: Int = R.string.carregando
) {
    // Guarda a cor original do botão (com os estados habilitado/desabilitado) só na primeira vez.
    if (getTag(R.id.tag_cor_original_botao) == null) setTag(R.id.tag_cor_original_botao, backgroundTintList)
    val corOriginal = getTag(R.id.tag_cor_original_botao) as ColorStateList?

    if (carregando) {
        if (text.isNotEmpty()) setTag(R.id.tag_texto_original_botao, text)
        text = ""
        contentDescription = context.getString(descricaoCarregando)
        corOriginal?.let {
            val corHabilitado = it.getColorForState(intArrayOf(android.R.attr.state_enabled), it.defaultColor)
            backgroundTintList = ColorStateList.valueOf(corHabilitado)
        }
    } else {
        val textoGuardado = getTag(R.id.tag_texto_original_botao) as CharSequence?
        if (text.isEmpty() && textoGuardado != null) text = textoGuardado
        setTag(R.id.tag_texto_original_botao, null)
        contentDescription = null
        backgroundTintList = corOriginal
    }

    isEnabled = habilitado && !carregando
    progresso.visibility = if (carregando && visibility == View.VISIBLE) View.VISIBLE else View.GONE
}
