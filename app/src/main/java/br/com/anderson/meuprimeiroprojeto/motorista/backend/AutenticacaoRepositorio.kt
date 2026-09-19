package br.com.anderson.meuprimeiroprojeto.motorista.backend

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore

/** Cadastro/login do motorista com Firebase Auth; o perfil (nome, cpf) fica no Firestore. */
object AutenticacaoRepositorio {

    private val auth get() = Firebase.auth
    private val db get() = Firebase.firestore

    val usuarioAtual: FirebaseUser?
        get() = auth.currentUser

    suspend fun cadastrar(nome: String, cpf: String, email: String, senha: String): FirebaseUser {
        val resultado = auth.createUserWithEmailAndPassword(email, senha).aguardar()
        val usuario = resultado.user ?: error("Não foi possível criar a conta")
        db.collection("motoristas").document(usuario.uid)
            .set(MotoristaPerfil(nome = nome, cpf = cpf))
            .aguardar()
        return usuario
    }

    suspend fun entrar(email: String, senha: String): FirebaseUser {
        val resultado = auth.signInWithEmailAndPassword(email, senha).aguardar()
        return resultado.user ?: error("Não foi possível entrar")
    }

    suspend fun buscarPerfil(uid: String): MotoristaPerfil? {
        val documento = db.collection("motoristas").document(uid).get().aguardar()
        return documento.toObject(MotoristaPerfil::class.java)
    }

    fun sair() = auth.signOut()
}
