package br.com.anderson.meuprimeiroprojeto.gerencial.backend

import br.com.anderson.meuprimeiroprojeto.gerencial.dados.Caminhao
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

object CaminhaoRepositorio {

    private val db get() = Firebase.firestore

    class PlacaJaCadastradaException : Exception("Já existe um caminhão com essa placa")

    suspend fun cadastrar(placa: String, marca: String, modelo: String, ano: Int) {
        val documento = db.collection("caminhoes").document(placa)
        if (documento.get().aguardar().exists()) {
            throw PlacaJaCadastradaException()
        }
        documento.set(CaminhaoRemoto(marca = marca, modelo = modelo, ano = ano)).aguardar()
    }

    suspend fun listar(): List<Caminhao> {
        val snapshot = db.collection("caminhoes").get().aguardar()
        return snapshot.documents.mapNotNull { documento ->
            val remoto = documento.toObject(CaminhaoRemoto::class.java) ?: return@mapNotNull null
            Caminhao(documento.id, remoto.marca, remoto.modelo, remoto.ano)
        }.sortedBy { it.placa }
    }
}
