package br.com.anderson.meuprimeiroprojeto.gerencial.backend

import br.com.anderson.meuprimeiroprojeto.gerencial.dados.Caminhao
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

/**
 * Cadastra e lista os caminhões da usina no Firestore (coleção `caminhoes`).
 *
 * "Repositório" é a classe que busca e grava dados; as telas pedem para o ViewModel, que pede
 * para cá. É um `object`: só existe uma cópia, usada direto pelo nome.
 */
object CaminhaoRepositorio {

    private val db get() = Firebase.firestore

    /** Erro lançado quando o gerente tenta cadastrar uma placa que já existe. */
    class PlacaJaCadastradaException : Exception("Já existe um caminhão com essa placa")

    class NumeroJaCadastradoException : Exception("Já existe um caminhão com esse número")

    /**
     * Cadastra um caminhão usando a placa como nome (id) do documento.
     *
     * Antes de gravar, confere se já existe um documento com essa placa. Isso é necessário porque
     * o `set` do Firestore não reclama de documento repetido: ele simplesmente substituiria o
     * caminhão que já estava lá.
     */
    suspend fun cadastrar(placa: String, numero: String, marca: String, modelo: String, ano: Int) {
        val colecao = db.collection("caminhoes")
        val documento = colecao.document(placa)
        val existente = documento.get().aguardar()
        if (existente.exists() && !existente.getString("numero").isNullOrBlank()) {
            throw PlacaJaCadastradaException()
        }
        val mesmoNumero = colecao.whereEqualTo("numero", numero).get().aguardar()
        if (mesmoNumero.documents.any { it.id != placa }) {
            throw NumeroJaCadastradoException()
        }
        documento.set(CaminhaoRemoto(numero = numero, marca = marca, modelo = modelo, ano = ano)).aguardar()
    }

    suspend fun listar(): List<Caminhao> {
        val snapshot = db.collection("caminhoes").get().aguardar()
        return snapshot.documents.mapNotNull { documento ->
            val remoto = documento.toObject(CaminhaoRemoto::class.java) ?: return@mapNotNull null
            Caminhao(documento.id, remoto.numero, remoto.marca, remoto.modelo, remoto.ano)
        }.sortedWith(compareBy({ it.numero.toIntOrNull() ?: Int.MAX_VALUE }, { it.numero }, { it.placa }))
    }
}
