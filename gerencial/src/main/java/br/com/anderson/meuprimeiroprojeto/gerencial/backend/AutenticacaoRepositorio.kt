package br.com.anderson.meuprimeiroprojeto.gerencial.backend

import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore

/**
 * Cuida do login do gerente e do cadastro de novos gerentes.
 *
 * O login (email e senha) é feito pelo Firebase Auth, um serviço do Google que guarda as contas e
 * senhas com segurança. O perfil (nome) fica no Firestore, na coleção `gerentes`.
 *
 * Ninguém se cadastra sozinho como gerente: um gerente novo só é criado por outro gerente que já
 * está logado (ver [cadastrarNovoGerente]).
 *
 * "Repositório" é a classe que busca e grava dados; as telas nunca falam com o Firebase direto.
 * É um `object`: só existe uma cópia, usada direto pelo nome.
 */
object AutenticacaoRepositorio {

    // Nome de uma segunda instância do Firebase, só para cadastrar outro gerente estando logado.
    private const val APP_SECUNDARIO = "CadastroGerente"

    // O `get()` faz o Kotlin buscar o Firebase toda vez que o campo é usado.
    private val auth get() = Firebase.auth
    private val db get() = Firebase.firestore

    /**
     * O gerente logado agora, ou `null` se ninguém estiver logado. O Firebase Auth lembra o login
     * mesmo depois de fechar o app.
     */
    val usuarioAtual: FirebaseUser?
        get() = auth.currentUser

    /**
     * Cria a conta de um gerente novo, a partir da aba Configurações, sem deslogar quem está
     * cadastrando.
     *
     * O problema: no Firebase, criar uma conta faz login automático com ela. Se isso acontecesse na
     * conexão principal do app, o gerente que está cadastrando seria "trocado" pelo novo. A solução
     * é abrir uma SEGUNDA conexão com o Firebase (uma "instância secundária", com o nome
     * [APP_SECUNDARIO]), criar a conta nela e depois deslogar dessa segunda conexão. A conexão
     * principal nem fica sabendo.
     *
     * Detalhe: as regras de segurança do Firestore só deixam cada gerente gravar o PRÓPRIO
     * documento em `gerentes`. Por isso o perfil é gravado pela segunda conexão, que está logada
     * como o gerente novo; pela conexão principal, a gravação seria recusada.
     *
     * O `try/catch` no começo reaproveita a segunda conexão se ela já tiver sido aberta antes
     * (abrir duas vezes com o mesmo nome daria erro).
     */
    suspend fun cadastrarNovoGerente(nome: String, email: String, senha: String) {
        val appPrincipal = FirebaseApp.getInstance()
        val appSecundario = try {
            FirebaseApp.getInstance(APP_SECUNDARIO)
        } catch (erro: IllegalStateException) {
            FirebaseApp.initializeApp(appPrincipal.applicationContext, appPrincipal.options, APP_SECUNDARIO)
        }

        val authSecundario = FirebaseAuth.getInstance(appSecundario)
        val resultado = authSecundario.createUserWithEmailAndPassword(email, senha).aguardar()
        val uid = resultado.user?.uid ?: error("Não foi possível criar a conta do gerente")

        Firebase.firestore(appSecundario).collection("gerentes").document(uid)
            .set(GerentePerfil(nome = nome))
            .aguardar()

        authSecundario.signOut()
    }

    /**
     * Faz o login do gerente e devolve o usuário logado. Se o email ou a senha estiverem errados, o
     * Firebase lança um erro, que o `LoginViewModel` transforma em mensagem.
     */
    suspend fun entrar(email: String, senha: String): FirebaseUser {
        val resultado = auth.signInWithEmailAndPassword(email, senha).aguardar()
        return resultado.user ?: error("Não foi possível entrar")
    }

    /** Busca o perfil (nome) do gerente no Firestore; devolve `null` se não existir. */
    suspend fun buscarPerfil(uid: String): GerentePerfil? {
        val documento = db.collection("gerentes").document(uid).get().aguardar()
        return documento.toObject(GerentePerfil::class.java)
    }

    /** Faz o logout: o Firebase Auth esquece o gerente que estava logado neste celular. */
    fun sair() = auth.signOut()
}
