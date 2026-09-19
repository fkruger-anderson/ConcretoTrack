package br.com.anderson.meuprimeiroprojeto.motorista.backend

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore

/** Cadastro/login do motorista com Firebase Auth; o perfil (nome, cpf) fica no Firestore. */
object AutenticacaoRepositorio {

    private val auth get() = Firebase.auth
    private val db get() = Firebase.firestore

    /** Usuário logado no momento, ou null se ninguém estiver logado. */
    val usuarioAtual: FirebaseUser?
        get() = auth.currentUser

    /** Cria a conta no Firebase Auth e grava o perfil (nome, cpf) no Firestore. */
    suspend fun cadastrar(nome: String, cpf: String, email: String, senha: String): FirebaseUser {
        val resultado = auth.createUserWithEmailAndPassword(email, senha).aguardar()
        val usuario = resultado.user ?: error("Não foi possível criar a conta")
        db.collection("motoristas").document(usuario.uid)
            .set(MotoristaPerfil(nome = nome, cpf = cpf))
            .aguardar()
        return usuario
    }

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
