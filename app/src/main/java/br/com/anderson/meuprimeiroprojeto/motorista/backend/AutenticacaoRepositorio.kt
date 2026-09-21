package br.com.anderson.meuprimeiroprojeto.motorista.backend

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore

/**
 * Login do motorista com Firebase Auth; o perfil (nome, cpf) fica no Firestore. O motorista não
 * se cadastra sozinho — a conta é criada pelo gerente no app gerencial (ver
 * `MotoristaRepositorio.cadastrar`, módulo `:gerencial`).
 */
object AutenticacaoRepositorio {

    private val auth get() = Firebase.auth
    private val db get() = Firebase.firestore

    /** Usuário logado no momento, ou null se ninguém estiver logado. */
    val usuarioAtual: FirebaseUser?
        get() = auth.currentUser

    /** Login com email/senha já cadastrados pelo gerente. */
    suspend fun entrar(email: String, senha: String): FirebaseUser {
        val resultado = auth.signInWithEmailAndPassword(email, senha).aguardar()
        return resultado.user ?: error("Não foi possível entrar")
    }

    /** Busca nome/cpf do motorista no Firestore a partir do uid do Firebase Auth. */
    suspend fun buscarPerfil(uid: String): MotoristaPerfil? {
        val documento = db.collection("motoristas").document(uid).get().aguardar()
        return documento.toObject(MotoristaPerfil::class.java)
    }

    /** Encerra a sessão do motorista no Firebase Auth. */
    fun sair() = auth.signOut()
}
