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

    /** Abre o arquivo de SharedPreferences usado para o cache do perfil. */
    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NOME, Context.MODE_PRIVATE)

    /** Grava nome/cpf em cache local, geralmente logo após buscar o perfil no Firestore. */
    fun salvar(context: Context, nome: String, cpf: String) {
        prefs(context).edit()
            .putString(CHAVE_NOME, nome)
            .putString(CHAVE_CPF, cpf)
            .apply()
    }

    /** True se já existe um perfil salvo em cache (evita nova busca no Firestore). */
    fun temPerfilEmCache(context: Context): Boolean =
        prefs(context).getString(CHAVE_NOME, null) != null

    /** Nome do motorista em cache, ou string vazia se ainda não foi salvo. */
    fun nomeMotorista(context: Context): String =
        prefs(context).getString(CHAVE_NOME, "") ?: ""

    /** CPF do motorista em cache, ou string vazia se ainda não foi salvo. */
    fun cpfMotorista(context: Context): String =
        prefs(context).getString(CHAVE_CPF, "") ?: ""

    /** Limpa o cache local ao sair (a sessão do Firebase Auth é encerrada à parte). */
    fun sair(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
