package br.com.anderson.meuprimeiroprojeto.gerencial.backend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Endereço devolvido pela ViaCEP a partir de um CEP, para preencher o formulário de pedido. */
data class EnderecoViaCep(
    val rua: String,
    val bairro: String,
    val cidade: String,
    val uf: String
)

/**
 * Busca endereço pelo CEP na ViaCEP. Chamada HTTP simples com HttpURLConnection —
 * não precisamos de uma biblioteca de rede (Retrofit etc.) só para isso.
 */
object ViaCepServico {

    /** Faz a requisição para o CEP informado; retorna null se o CEP não existir. Roda em Dispatchers.IO por ser I/O de rede bloqueante. */
    suspend fun buscar(cep: String): EnderecoViaCep? = withContext(Dispatchers.IO) {
        val conexao = URL("https://viacep.com.br/ws/$cep/json/").openConnection() as HttpURLConnection
        try {
            conexao.connectTimeout = 8_000
            conexao.readTimeout = 8_000
            val corpo = conexao.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(corpo)
            if (json.optBoolean("erro", false)) return@withContext null

            EnderecoViaCep(
                rua = json.optString("logradouro"),
                bairro = json.optString("bairro"),
                cidade = json.optString("localidade"),
                uf = json.optString("uf")
            )
        } finally {
            conexao.disconnect()
        }
    }
}
