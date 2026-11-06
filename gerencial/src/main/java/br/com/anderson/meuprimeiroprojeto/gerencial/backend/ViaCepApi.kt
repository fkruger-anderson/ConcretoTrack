package br.com.anderson.meuprimeiroprojeto.gerencial.backend

import retrofit2.http.GET
import retrofit2.http.Path

interface ViaCepApi {

    @GET("ws/{cep}/json/")
    suspend fun buscar(@Path("cep") cep: String): EnderecoCep
}
