package br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel

/**
 * Uma "embalagem" para avisos que a tela deve tratar uma única vez, como mostrar um Toast ou
 * fechar a tela (o app do motorista tem uma cópia igual).
 *
 * O problema: o `LiveData` sempre entrega o último valor para quem começa a observar. Quando o
 * celular gira, a tela é recriada, observa de novo e receberia outra vez o aviso antigo — fechando
 * a tela ou mostrando o Toast duas vezes.
 *
 * Com o `Evento`, a primeira leitura "consome" o conteúdo; as seguintes recebem `null`.
 *
 * `<out T>`: funciona para qualquer tipo de conteúdo. `Unit` é o tipo "sem valor" do Kotlin, usado
 * quando só importa que o aviso aconteceu.
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
