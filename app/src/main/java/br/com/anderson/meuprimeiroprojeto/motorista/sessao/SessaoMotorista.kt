package br.com.anderson.meuprimeiroprojeto.motorista.sessao

import android.content.Context
import android.content.SharedPreferences

/**
 * Guarda no celular o nome do motorista logado, para não precisar buscar no Firestore toda vez
 * que uma tela quiser mostrar o nome.
 *
 * Usa as SharedPreferences: um arquivinho do Android que guarda pares "chave = valor" de tipos
 * simples (texto, número, verdadeiro/falso). É o lugar certo para configurações e dados pequenos;
 * listas e registros maiores ficam no banco de dados.
 *
 * O arquivo é aberto uma vez só, em [inicializar], quando o app começa a rodar
 * (`MotoristaApplication`). Por isso as outras funções não precisam receber um `Context` — e os
 * ViewModels, que não podem depender de `Context`, conseguem usá-las.
 *
 * Importante: quem decide se o motorista está logado é o Firebase Auth
 * (`AutenticacaoRepositorio.usuarioAtual`), não este arquivo. Aqui é só uma "cópia de
 * conveniência".
 */
object SessaoMotorista {

    // Nome do arquivo e da chave. Ficam em constantes para não haver erro de digitação
    // (escrever "nome" num lugar e "Nome" em outro seriam duas chaves diferentes).
    private const val PREFS_NOME = "sessao_motorista"
    private const val CHAVE_NOME = "nome"

    // O arquivo de preferências, aberto em inicializar(). `lateinit` = preenchido depois.
    private lateinit var prefs: SharedPreferences

    /**
     * Abre o arquivo de preferências. Chamada uma única vez pela `MotoristaApplication`.
     * `MODE_PRIVATE` quer dizer que só este app consegue ler o arquivo.
     */
    fun inicializar(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NOME, Context.MODE_PRIVATE)
    }

    /**
     * Grava o nome, normalmente logo depois do login. O `edit()` abre a edição, o `putString`
     * escreve o valor e o `apply()` salva no arquivo sem travar a tela.
     */
    fun salvar(nome: String) {
        prefs.edit()
            .putString(CHAVE_NOME, nome)
            .apply()
    }

    /** Devolve o nome guardado, ou texto vazio se nada foi guardado ainda. */
    fun nomeMotorista(): String =
        prefs.getString(CHAVE_NOME, "") ?: ""

    /** Apaga tudo o que foi guardado, na hora do logout (o logout no Firebase Auth é feito à parte). */
    fun sair() {
        prefs.edit().clear().apply()
    }
}
