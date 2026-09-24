package br.com.anderson.meuprimeiroprojeto.gerencial.backend

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

/**
 * Um motorista junto com o seu `uid` (o código da conta no Firebase Auth).
 *
 * O `uid` não fica dentro de [MotoristaPerfil] porque, no Firestore, ele é o nome do documento, e
 * não um campo. Esta classe junta os dois para a tela de atribuir viagem.
 */
data class MotoristaComId(val uid: String, val perfil: MotoristaPerfil) {
    /**
     * O texto que representa o objeto. A caixa de seleção (spinner) usa o `toString()` de cada
     * item para mostrar na lista; por isso ele devolve só o nome do motorista.
     */
    override fun toString(): String = perfil.nome
}

/**
 * Cadastra e lista os motoristas (coleção `motoristas` no Firestore e contas no Firebase Auth).
 * Quem cadastra motoristas é sempre o gerente.
 *
 * É um `object`: só existe uma cópia, usada direto pelo nome.
 */
object MotoristaRepositorio {

    // Nome de uma segunda instância do Firebase, só para criar contas de motorista.
    private const val APP_SECUNDARIO = "CadastroMotorista"

    private val db get() = Firebase.firestore

    /**
     * Cria a conta de login do motorista (email e senha) e o perfil dele (nome e CPF).
     *
     * O problema: no Firebase, criar uma conta faz login automático com ela. Se isso acontecesse
     * na conexão principal do app, o gerente seria deslogado e ficaria logado como o motorista
     * novo. A solução é abrir uma SEGUNDA conexão com o Firebase (a "instância secundária"), criar
     * a conta por ela e depois deslogar dela. A conexão principal, onde o gerente está, não muda.
     *
     * O perfil é gravado pela conexão principal (a do gerente), porque as regras de segurança
     * deixam o gerente escrever em `motoristas`.
     */
    suspend fun cadastrar(nome: String, cpf: String, email: String, senha: String) {
        val appPrincipal = FirebaseApp.getInstance()
        val appSecundario = try {
            FirebaseApp.getInstance(APP_SECUNDARIO)
        } catch (erro: IllegalStateException) {
            FirebaseApp.initializeApp(appPrincipal.applicationContext, appPrincipal.options, APP_SECUNDARIO)
        }

        val authSecundario = FirebaseAuth.getInstance(appSecundario)
        val resultado = authSecundario.createUserWithEmailAndPassword(email, senha).aguardar()
        val uid = resultado.user?.uid ?: error("Não foi possível criar a conta do motorista")
        authSecundario.signOut()

        db.collection("motoristas").document(uid)
            .set(MotoristaPerfil(nome = nome, cpf = cpf))
            .aguardar()
    }

    /** Lista todos os motoristas, em ordem alfabética, para a caixa de seleção da atribuição. */
    suspend fun listar(): List<MotoristaComId> {
        val snapshot = db.collection("motoristas").get().aguardar()
        return snapshot.documents.mapNotNull { documento ->
            val perfil = documento.toObject(MotoristaPerfil::class.java) ?: return@mapNotNull null
            MotoristaComId(documento.id, perfil)
        }.sortedBy { it.perfil.nome }
    }
}
