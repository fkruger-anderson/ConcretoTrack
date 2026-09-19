package br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel

/**
 * Envelope para um valor que a View deve tratar (mostrar toast, navegar) só uma vez.
 * Sem isso, um LiveData comum reentrega o último valor pra quem observar de novo — por
 * exemplo, se a tela girar logo depois de um sucesso, a navegação disparia outra vez à toa.
 * Aqui, a primeira leitura "consome" o conteúdo; leituras seguintes recebem null.
 */
class Evento<out T>(private val conteudo: T) {

    private var tratado = false

    /** Retorna o conteúdo só na primeira chamada; depois disso, sempre retorna null. */
    fun getConteudoSeNaoTratado(): T? {
        if (tratado) return null
        tratado = true
        return conteudo
    }
}
