package br.com.anderson.meuprimeiroprojeto.gerencial.backend

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

/** Perfil do motorista junto com o uid do documento — o uid não faz parte do [MotoristaPerfil] porque é o id do próprio documento no Firestore. */
data class MotoristaComId(val uid: String, val perfil: MotoristaPerfil) {
    /** Mostra só o nome, para aparecer direto no spinner de seleção de motorista. */
    override fun toString(): String = perfil.nome
}

/** Cadastro e listagem de motoristas, feitos pelo gerente. */
object MotoristaRepositorio {

    // Nome de uma segunda instância do Firebase, só para criar contas de motorista.
    private const val APP_SECUNDARIO = "CadastroMotorista"

    private val db get() = Firebase.firestore

    /**
     * Cria a conta do motorista no Firebase Auth e o perfil (nome, cpf) no Firestore.
     *
     * `createUserWithEmailAndPassword` loga automaticamente com a conta criada — se
     * chamássemos isso na instância padrão do Firebase, o gerente logado seria substituído
     * pelo motorista novo. Por isso usamos uma instância secundária do FirebaseApp só para
     * essa chamada: ela tem sua própria sessão, isolada da instância principal (onde o
     * gerente continua logado o tempo todo).
     */
    suspend fun cadastrar(context: Context, nome: String, cpf: String, email: String, senha: String) {
        val appPrincipal = FirebaseApp.getInstance()
        val appSecundario = try {
            FirebaseApp.getInstance(APP_SECUNDARIO)
        } catch (erro: IllegalStateException) {
            FirebaseApp.initializeApp(context.applicationContext, appPrincipal.options, APP_SECUNDARIO)
        }

        val authSecundario = FirebaseAuth.getInstance(appSecundario)
        val resultado = authSecundario.createUserWithEmailAndPassword(email, senha).aguardar()
        val uid = resultado.user?.uid ?: error("Não foi possível criar a conta do motorista")
        authSecundario.signOut()

        db.collection("motoristas").document(uid)
            .set(MotoristaPerfil(nome = nome, cpf = cpf))
            .aguardar()
    }

    /** Lista todos os motoristas cadastrados, ordenados por nome (para o spinner de atribuição). */
    suspend fun listar(): List<MotoristaComId> {
        val snapshot = db.collection("motoristas").get().aguardar()
        return snapshot.documents.mapNotNull { documento ->
            val perfil = documento.toObject(MotoristaPerfil::class.java) ?: return@mapNotNull null
            MotoristaComId(documento.id, perfil)
        }.sortedBy { it.perfil.nome }
    }
}
