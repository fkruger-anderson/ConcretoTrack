package br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.PedidoRepositorio
import br.com.anderson.meuprimeiroprojeto.gerencial.dados.DocumentoUtils
import br.com.anderson.meuprimeiroprojeto.gerencial.dados.Uf
import kotlinx.coroutines.launch

/** Estado exibido por [NovoPedidoActivity][br.com.anderson.meuprimeiroprojeto.gerencial.ui.NovoPedidoActivity]. */
data class NovoPedidoUiState(
    val carregando: Boolean = false,
    val erroDocumento: Boolean = false,
    val erroNome: Boolean = false,
    val erroEndereco: Boolean = false,
    val erroQuantidade: Boolean = false,
    val erroDistanciaKm: Boolean = false,
    val erroDataEntrega: Boolean = false,
    val erroSalvar: Boolean = false,
    val sucesso: Evento<Int>? = null
)

/** Valida os campos (documento, endereço digitado à mão, quantidade) e cadastra o pedido no Firestore. */
class NovoPedidoViewModel : ViewModel() {

    companion object {
        private const val TAG = "NovoPedidoViewModel"
    }

    private val _estado = MutableLiveData(NovoPedidoUiState())
    val estado: LiveData<NovoPedidoUiState> = _estado

    /**
     * Valida documento (CPF/CNPJ, com dígito verificador), nome, endereço e quantidade; só chama
     * o repositório se tudo estiver certo. Cadastrar pedido precisa de conexão na hora, então um
     * erro genérico aqui vira mensagem de "não deu pra salvar" (sem fila offline, diferente dos
     * pontos de GPS do motorista).
     */
    fun salvar(
        documentoDigitado: String,
        nome: String,
        cepDigitado: String,
        rua: String,
        numero: String,
        bairro: String,
        cidade: String,
        ufDigitada: String,
        quantidadeDigitada: String,
        distanciaDigitada: String,
        dataEntrega: Long?
    ) {
        val documento = DocumentoUtils.apenasDigitos(documentoDigitado)
        val tipoPessoa = DocumentoUtils.tipoPessoa(documento)
        val documentoInvalido = tipoPessoa == null || !DocumentoUtils.documentoValido(documento)
        val nomeInvalido = nome.isEmpty()

        val cep = DocumentoUtils.apenasDigitos(cepDigitado)
        val uf = ufDigitada.trim().uppercase()
        val enderecoInvalido = cep.length != 8 || rua.isEmpty() || numero.isEmpty() ||
            bairro.isEmpty() || cidade.isEmpty() || uf !in Uf.siglas

        val quantidadeM3 = quantidadeDigitada.trim().replace(',', '.').toDoubleOrNull()
        val quantidadeInvalida = quantidadeM3 == null || quantidadeM3 <= 0.0

        val distanciaKm = distanciaDigitada.trim().replace(',', '.').toDoubleOrNull()
        val distanciaInvalida = distanciaKm == null || distanciaKm <= 0.0

        val dataEntregaInvalida = dataEntrega == null

        if (documentoInvalido || nomeInvalido || enderecoInvalido || quantidadeInvalida ||
            distanciaInvalida || dataEntregaInvalida
        ) {
            _estado.value = _estado.value?.copy(
                erroDocumento = documentoInvalido,
                erroNome = nomeInvalido,
                erroEndereco = enderecoInvalido,
                erroQuantidade = quantidadeInvalida,
                erroDistanciaKm = distanciaInvalida,
                erroDataEntrega = dataEntregaInvalida,
                erroSalvar = false
            )
            return
        }

        _estado.value = _estado.value?.copy(
            carregando = true,
            erroDocumento = false,
            erroNome = false,
            erroEndereco = false,
            erroQuantidade = false,
            erroDistanciaKm = false,
            erroDataEntrega = false,
            erroSalvar = false
        )
        viewModelScope.launch {
            try {
                val id = PedidoRepositorio.cadastrar(
                    destinatarioNome = nome,
                    documento = documento,
                    tipoPessoa = requireNotNull(tipoPessoa),
                    rua = rua,
                    numero = numero,
                    bairro = bairro,
                    cidade = cidade,
                    uf = uf,
                    cep = cep,
                    quantidadeTotalM3 = requireNotNull(quantidadeM3),
                    dataEntrega = requireNotNull(dataEntrega),
                    distanciaDedicadaKm = requireNotNull(distanciaKm)
                )
                _estado.value = _estado.value?.copy(carregando = false, sucesso = Evento(id))
            } catch (erro: Exception) {
                Log.w(TAG, "Falha ao cadastrar pedido", erro)
                _estado.value = _estado.value?.copy(carregando = false, erroSalvar = true)
            }
        }
    }
}
