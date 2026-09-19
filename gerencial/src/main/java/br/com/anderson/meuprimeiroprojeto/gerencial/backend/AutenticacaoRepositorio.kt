package br.com.anderson.meuprimeiroprojeto.gerencial.backend

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore

/** Cadastro/login do gerente com Firebase Auth; o perfil (nome) fica no Firestore. */
object AutenticacaoRepositorio {

    // Nome de uma segunda instância do Firebase, só para cadastrar outro gerente estando logado.
    private const val APP_SECUNDARIO = "CadastroGerente"

    private val auth get() = Firebase.auth
    private val db get() = Firebase.firestore

    /** Usuário do Firebase Auth logado no momento, ou null se ninguém estiver logado. */
    val usuarioAtual: FirebaseUser?
        get() = auth.currentUser

    /** Autocadastro na tela de login (ninguém está logado ainda, então pode usar a instância principal). */
    suspend fun cadastrar(nome: String, email: String, senha: String): FirebaseUser {
        val resultado = auth.createUserWithEmailAndPassword(email, senha).aguardar()
        val usuario = resultado.user ?: error("Não foi possível criar a conta")
        db.collection("gerentes").document(usuario.uid)
            .set(GerentePerfil(nome = nome))
            .aguardar()
        return usuario
    }

    /**
     * Cadastra outro gerente a partir da aba Configurações, com o gerente atual já logado.
     * Mesmo raciocínio do [MotoristaRepositorio.cadastrar]: usa uma instância secundária do
     * Firebase para não substituir a sessão de quem está criando a conta.
     *
     * Diferente do motorista: a regra de segurança de `gerentes/{id}` só permite que o próprio
     * dono escreva o próprio documento (motorista é gerenciado pelo gerente; gerente não).
     * Por isso a gravação do perfil usa a instância secundária (ainda autenticada como o
     * gerente novo) em vez da principal — se gravássemos pela sessão de quem está cadastrando,
     * a escrita seria feita com o uid errado e a regra rejeitaria.
     */
    suspend fun cadastrarNovoGerente(context: Context, nome: String, email: String, senha: String) {
        val appPrincipal = FirebaseApp.getInstance()
        val appSecundario = try {
            FirebaseApp.getInstance(APP_SECUNDARIO)
        } catch (erro: IllegalStateException) {
            FirebaseApp.initializeApp(context.applicationContext, appPrincipal.options, APP_SECUNDARIO)
        }

        val authSecundario = FirebaseAuth.getInstance(appSecundario)
        val resultado = authSecundario.createUserWithEmailAndPassword(email, senha).aguardar()
        val uid = resultado.user?.uid ?: error("Não foi possível criar a conta do gerente")

        Firebase.firestore(appSecundario).collection("gerentes").document(uid)
            .set(GerentePerfil(nome = nome))
            .aguardar()

        authSecundario.signOut()
    }

    /** Login do gerente com email/senha já cadastrados. */
    suspend fun entrar(email: String, senha: String): FirebaseUser {
        val resultado = auth.signInWithEmailAndPassword(email, senha).aguardar()
        return resultado.user ?: error("Não foi possível entrar")
    }

    /** Busca o perfil (nome) do gerente pelo uid, para exibir na tela depois do login. */
    suspend fun buscarPerfil(uid: String): GerentePerfil? {
        val documento = db.collection("gerentes").document(uid).get().aguardar()
        return documento.toObject(GerentePerfil::class.java)
    }

    /** Encerra a sessão do gerente logado. */
    fun sair() = auth.signOut()
}
