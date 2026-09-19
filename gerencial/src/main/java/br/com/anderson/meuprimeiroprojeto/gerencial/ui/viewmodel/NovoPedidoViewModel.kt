package br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.PedidoRepositorio
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.ViaCepServico
import br.com.anderson.meuprimeiroprojeto.gerencial.dados.DocumentoUtils
import br.com.anderson.meuprimeiroprojeto.gerencial.dados.Uf
import kotlinx.coroutines.launch

/** Endereço vindo da ViaCEP, pronto pra preencher os campos — um evento porque só deve ser
 * aplicado uma vez (senão sobrescreveria o que o gerente já tiver editado depois da busca). */
data class EnderecoPreenchido(val rua: String, val bairro: String, val cidade: String, val uf: String)

/** Estado exibido por [NovoPedidoActivity][br.com.anderson.meuprimeiroprojeto.gerencial.ui.NovoPedidoActivity]. */
data class NovoPedidoUiState(
    val buscandoCep: Boolean = false,
    val erroCep: Boolean = false,
    val enderecoEncontrado: Evento<EnderecoPreenchido>? = null,
    val carregando: Boolean = false,
    val erroDocumento: Boolean = false,
    val erroNome: Boolean = false,
    val erroEndereco: Boolean = false,
    val erroQuantidade: Boolean = false,
    val erroDataEntrega: Boolean = false,
    val erroSalvar: Boolean = false,
    val sucesso: Evento<Int>? = null
)

/** Valida os campos (documento, endereço, quantidade) e cadastra o pedido no Firestore. */
class NovoPedidoViewModel : ViewModel() {

    private val _estado = MutableLiveData(NovoPedidoUiState())
    val estado: LiveData<NovoPedidoUiState> = _estado

    /** Consulta a ViaCEP e, se achar o endereço, expõe como [Evento] pra preencher os campos uma vez. */
    fun buscarCep(cepDigitado: String) {
        val cep = DocumentoUtils.apenasDigitos(cepDigitado)
        if (cep.length != 8) {
            _estado.value = _estado.value?.copy(erroCep = true)
            return
        }

        _estado.value = _estado.value?.copy(buscandoCep = true, erroCep = false)
        viewModelScope.launch {
            val endereco = try {
                ViaCepServico.buscar(cep)
            } catch (erro: Exception) {
                null
            }

            if (endereco == null) {
                _estado.value = _estado.value?.copy(buscandoCep = false, erroCep = true)
                return@launch
            }
            _estado.value = _estado.value?.copy(
                buscandoCep = false,
                erroCep = false,
                enderecoEncontrado = Evento(
                    EnderecoPreenchido(endereco.rua, endereco.bairro, endereco.cidade, endereco.uf)
                )
            )
        }
    }

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

        val dataEntregaInvalida = dataEntrega == null

        if (documentoInvalido || nomeInvalido || enderecoInvalido || quantidadeInvalida || dataEntregaInvalida) {
            _estado.value = _estado.value?.copy(
                erroDocumento = documentoInvalido,
                erroNome = nomeInvalido,
                erroEndereco = enderecoInvalido,
                erroQuantidade = quantidadeInvalida,
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
                    dataEntrega = requireNotNull(dataEntrega)
                )
                _estado.value = _estado.value?.copy(carregando = false, sucesso = Evento(id))
            } catch (erro: Exception) {
                _estado.value = _estado.value?.copy(carregando = false, erroSalvar = true)
            }
        }
    }
}
