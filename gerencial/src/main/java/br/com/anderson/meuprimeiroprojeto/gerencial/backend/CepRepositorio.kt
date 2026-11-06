package br.com.anderson.meuprimeiroprojeto.gerencial.backend

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object CepRepositorio {

    private const val URL_BASE = "https://viacep.com.br/"

    private val api: ViaCepApi = Retrofit.Builder()
        .baseUrl(URL_BASE)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ViaCepApi::class.java)

    suspend fun buscar(cep: String): EnderecoCep? =
        api.buscar(cep).takeIf { it.erro != true }
}
