package br.com.anderson.meuprimeiroprojeto.gerencial.backend

import br.com.anderson.meuprimeiroprojeto.gerencial.dados.Caminhao
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

/** Cadastro e listagem de caminhões, feitos pelo gerente. */
object CaminhaoRepositorio {

    private val db get() = Firebase.firestore

    /** Lançada quando o gerente tenta cadastrar uma placa que já existe (id do documento é a placa). */
    class PlacaJaCadastradaException : Exception("Já existe um caminhão com essa placa")

    /**
     * Cadastra o caminhão usando a placa como id do documento — por isso é preciso checar
     * antes se já existe, senão um `set` simplesmente sobrescreveria o caminhão existente.
     */
    suspend fun cadastrar(placa: String, marca: String, modelo: String, ano: Int) {
        val documento = db.collection("caminhoes").document(placa)
        if (documento.get().aguardar().exists()) {
            throw PlacaJaCadastradaException()
        }
        documento.set(CaminhaoRemoto(marca = marca, modelo = modelo, ano = ano)).aguardar()
    }

    /** Lista todos os caminhões cadastrados, ordenados por placa (para o spinner de atribuição). */
    suspend fun listar(): List<Caminhao> {
        val snapshot = db.collection("caminhoes").get().aguardar()
        return snapshot.documents.mapNotNull { documento ->
            val remoto = documento.toObject(CaminhaoRemoto::class.java) ?: return@mapNotNull null
            Caminhao(documento.id, remoto.marca, remoto.modelo, remoto.ano)
        }.sortedBy { it.placa }
    }
}
