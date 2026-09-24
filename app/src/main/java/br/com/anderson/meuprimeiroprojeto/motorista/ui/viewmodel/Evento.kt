package br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel

/**
 * Uma "embalagem" para avisos que a tela deve tratar uma única vez, como mostrar um Toast ou
 * fechar a tela.
 *
 * O problema que ela resolve: o `LiveData` (a caixinha observável do ViewModel) sempre entrega o
 * último valor para quem começa a observar. Quando o celular gira, a tela é recriada e começa a
 * observar de novo — e receberia outra vez o aviso antigo, mostrando o mesmo Toast duas vezes.
 *
 * Com o `Evento`, a primeira leitura "consome" o conteúdo; as leituras seguintes recebem `null` e
 * não fazem nada.
 *
 * `<out T>`: a embalagem funciona para qualquer tipo de conteúdo (um texto, um número, `Unit`...).
 * `Unit` é o tipo "sem valor" do Kotlin, usado quando só importa que o aviso aconteceu.
 */
class Evento<out T>(private val conteudo: T) {

    // Vira `true` depois da primeira leitura.
    private var tratado = false

    /** Devolve o conteúdo só na primeira vez que é chamada; depois disso, sempre devolve `null`. */
    fun getConteudoSeNaoTratado(): T? {
        if (tratado) return null
        tratado = true
        return conteudo
    }
}
