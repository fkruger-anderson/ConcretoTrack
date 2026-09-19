package br.com.anderson.meuprimeiroprojeto.motorista.sessao

import android.content.Context

/**
 * Cache local de nome/cpf do motorista logado, só para não precisar buscar no
 * Firestore em toda tela. Quem decide se está logado é o Firebase Auth
 * ([br.com.anderson.meuprimeiroprojeto.motorista.backend.AutenticacaoRepositorio]).
 */
object SessaoMotorista {

    private const val PREFS_NOME = "sessao_motorista"
    private const val CHAVE_NOME = "nome"
    private const val CHAVE_CPF = "cpf"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NOME, Context.MODE_PRIVATE)

    fun salvar(context: Context, nome: String, cpf: String) {
        prefs(context).edit()
            .putString(CHAVE_NOME, nome)
            .putString(CHAVE_CPF, cpf)
            .apply()
    }

    fun temPerfilEmCache(context: Context): Boolean =
        prefs(context).getString(CHAVE_NOME, null) != null

    fun nomeMotorista(context: Context): String =
        prefs(context).getString(CHAVE_NOME, "") ?: ""

    fun cpfMotorista(context: Context): String =
        prefs(context).getString(CHAVE_CPF, "") ?: ""

    fun sair(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
