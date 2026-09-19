package br.com.anderson.meuprimeiroprojeto.gerencial.backend

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore

/** Cadastro/login do gerente com Firebase Auth; o perfil (nome) fica no Firestore. */
object AutenticacaoRepositorio {

    private val auth get() = Firebase.auth
    private val db get() = Firebase.firestore

    val usuarioAtual: FirebaseUser?
        get() = auth.currentUser

    suspend fun cadastrar(nome: String, email: String, senha: String): FirebaseUser {
        val resultado = auth.createUserWithEmailAndPassword(email, senha).aguardar()
        val usuario = resultado.user ?: error("Não foi possível criar a conta")
        db.collection("gerentes").document(usuario.uid)
            .set(GerentePerfil(nome = nome))
            .aguardar()
        return usuario
    }

    suspend fun entrar(email: String, senha: String): FirebaseUser {
        val resultado = auth.signInWithEmailAndPassword(email, senha).aguardar()
        return resultado.user ?: error("Não foi possível entrar")
    }

    suspend fun buscarPerfil(uid: String): GerentePerfil? {
        val documento = db.collection("gerentes").document(uid).get().aguardar()
        return documento.toObject(GerentePerfil::class.java)
    }

    fun sair() = auth.signOut()
}
