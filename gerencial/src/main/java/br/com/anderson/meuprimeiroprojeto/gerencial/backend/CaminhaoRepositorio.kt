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

    /**
     * Cadastra um caminhão usando a placa como nome (id) do documento.
     *
     * Antes de gravar, confere se já existe um documento com essa placa. Isso é necessário porque
     * o `set` do Firestore não reclama de documento repetido: ele simplesmente substituiria o
     * caminhão que já estava lá.
     */
    suspend fun cadastrar(placa: String, marca: String, modelo: String, ano: Int) {
        val documento = db.collection("caminhoes").document(placa)
        if (documento.get().aguardar().exists()) {
            throw PlacaJaCadastradaException()
        }
        documento.set(CaminhaoRemoto(marca = marca, modelo = modelo, ano = ano)).aguardar()
    }

    /**
     * Lista todos os caminhões cadastrados, em ordem de placa, para a caixa de seleção (spinner)
     * da tela de atribuir viagem. Junta o id do documento (a placa) com os dados de cada um.
     */
    suspend fun listar(): List<Caminhao> {
        val snapshot = db.collection("caminhoes").get().aguardar()
        return snapshot.documents.mapNotNull { documento ->
            val remoto = documento.toObject(CaminhaoRemoto::class.java) ?: return@mapNotNull null
            Caminhao(documento.id, remoto.marca, remoto.modelo, remoto.ano)
        }.sortedBy { it.placa }
    }
}
