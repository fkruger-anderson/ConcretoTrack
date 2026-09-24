package br.com.anderson.meuprimeiroprojeto.motorista.backend

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore

/**
 * Cuida do login do motorista.
 *
 * O login (email e senha) é feito pelo Firebase Auth, um serviço do Google que guarda as contas e
 * as senhas com segurança: o app nunca guarda a senha. Os dados do perfil (como o nome) ficam à
 * parte, no Firestore, na coleção `motoristas`.
 *
 * O motorista não cria a própria conta. Quem cria é o gerente, pelo app gerencial (ver
 * `MotoristaRepositorio.cadastrar`, no módulo `:gerencial`).
 *
 * "Repositório" é o nome que a arquitetura do projeto dá às classes que buscam e gravam dados.
 * A tela pede para o ViewModel, o ViewModel pede para o repositório, e só o repositório fala
 * com o Firebase. Assim, se um dia o banco mudar, só os repositórios precisam mudar.
 *
 * É um `object`: só existe uma cópia dele no app inteiro, usada direto pelo nome
 * (`AutenticacaoRepositorio.entrar(...)`), sem precisar criar nada antes.
 */
object AutenticacaoRepositorio {

    // O `get()` faz o Kotlin buscar o Firebase toda vez que o campo é usado, em vez de guardar
    // uma cópia quando o objeto é criado.
    private val auth get() = Firebase.auth
    private val db get() = Firebase.firestore

    /**
     * O usuário que está logado agora, ou `null` se ninguém estiver logado.
     *
     * O Firebase Auth lembra o login mesmo depois que o app é fechado. Por isso este valor já vem
     * preenchido quando o motorista abre o app de novo, e a tela de login pode ser pulada.
     */
    val usuarioAtual: FirebaseUser?
        get() = auth.currentUser

    /**
     * Faz o login com o email e a senha que o gerente cadastrou e devolve o usuário logado.
     *
     * É `suspend` porque precisa esperar a resposta do servidor sem travar a tela. Se a senha
     * estiver errada, o Firebase lança um erro (uma "exceção"), que o `LoginViewModel` captura e
     * transforma numa mensagem para o motorista.
     */
    suspend fun entrar(email: String, senha: String): FirebaseUser {
        val resultado = auth.signInWithEmailAndPassword(email, senha).aguardar()
        return resultado.user ?: error("Não foi possível entrar")
    }

    /**
     * Busca o perfil (o nome) do motorista no Firestore a partir do `uid` (o código único que o
     * Firebase Auth dá para cada conta). Devolve `null` se o documento não existir.
     */
    suspend fun buscarPerfil(uid: String): MotoristaPerfil? {
        val documento = db.collection("motoristas").document(uid).get().aguardar()
        return documento.toObject(MotoristaPerfil::class.java)
    }

    /** Faz o logout: o Firebase Auth esquece o usuário que estava logado neste celular. */
    fun sair() = auth.signOut()
}
