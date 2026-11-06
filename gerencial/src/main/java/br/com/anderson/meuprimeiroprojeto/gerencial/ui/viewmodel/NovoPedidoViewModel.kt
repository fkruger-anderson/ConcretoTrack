package br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.CepRepositorio
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.EnderecoCep
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.PedidoRepositorio
import br.com.anderson.meuprimeiroprojeto.gerencial.dados.DocumentoUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.IOException

enum class ErroCep {
    NAO_ENCONTRADO,
    FORA_DA_AREA,
    SEM_CONEXAO
}

/**
 * O estado da tela de novo pedido ([NovoPedidoActivity][br.com.anderson.meuprimeiroprojeto.gerencial.ui.NovoPedidoActivity]).
 *
 * Cada `erroXxx` diz se aquele campo está com problema; a tela marca o campo em vermelho com o
 * texto certo. Assim o ViewModel não precisa saber nada de textos nem de cores.
 */
data class NovoPedidoUiState(
    /** `true` enquanto o pedido está sendo salvo. */
    val carregando: Boolean = false,
    val erroDocumento: Boolean = false,
    val erroNome: Boolean = false,
    /** Qualquer problema no endereço (CEP, rua, número, bairro ou cidade). */
    val erroEndereco: Boolean = false,
    val erroQuantidade: Boolean = false,
    val erroDistanciaKm: Boolean = false,
    val erroDataEntrega: Boolean = false,
    /** Os campos estavam certos, mas não deu para gravar (por exemplo, sem internet). */
    val erroSalvar: Boolean = false,
    /** Aviso de sucesso, levando o número que o pedido recebeu; tratado uma vez só. */
    val sucesso: Evento<Int>? = null,
    val enderecoCep: Evento<EnderecoCep>? = null,
    val erroCep: Evento<ErroCep>? = null,
    val buscandoCep: Boolean = false
)

/**
 * O "cérebro" da tela de novo pedido: confere todos os campos e, se estiver tudo certo, cadastra
 * o pedido no Firestore.
 */
class NovoPedidoViewModel : ViewModel() {

    companion object {
        // Etiqueta das mensagens desta classe no Logcat.
        private const val TAG = "NovoPedidoViewModel"
    }

    private var ultimoCepBuscado: String? = null
    private var buscaCep: Job? = null

    // `_estado` é a versão que o ViewModel pode mudar; `estado` é a versão só de leitura da tela.
    private val _estado = MutableLiveData(NovoPedidoUiState())
    /** O estado atual da tela; a tela é avisada toda vez que ele muda. */
    val estado: LiveData<NovoPedidoUiState> = _estado

    fun buscarCep(cepDigitado: String, cidadesAtendidas: List<String>) {
        val cep = DocumentoUtils.apenasDigitos(cepDigitado)
        if (cep.length != 8) {
            if (ultimoCepBuscado != null) {
                buscaCep?.cancel()
                buscaCep = null
                ultimoCepBuscado = null
                _estado.value = _estado.value?.copy(buscandoCep = false)
            }
            return
        }
        if (cep == ultimoCepBuscado) return
        ultimoCepBuscado = cep
        buscaCep?.cancel()
        _estado.value = _estado.value?.copy(buscandoCep = true)

        buscaCep = viewModelScope.launch {
            try {
                val endereco = CepRepositorio.buscar(cep)
                if (endereco == null) {
                    publicarErroCep(ErroCep.NAO_ENCONTRADO)
                    return@launch
                }
                val cidade = cidadesAtendidas.firstOrNull { it.equals(endereco.localidade?.trim(), ignoreCase = true) }
                if (cidade == null) {
                    publicarErroCep(ErroCep.FORA_DA_AREA)
                    return@launch
                }
                _estado.value = _estado.value?.copy(enderecoCep = Evento(endereco.copy(localidade = cidade)))
            } catch (erro: CancellationException) {
                throw erro
            } catch (erro: IOException) {
                publicarErroCep(ErroCep.SEM_CONEXAO)
            } catch (erro: Exception) {
                Log.w(TAG, "Falha ao buscar o CEP $cep", erro)
                publicarErroCep(ErroCep.NAO_ENCONTRADO)
            } finally {
                if (coroutineContext[Job] === buscaCep) {
                    buscaCep = null
                    _estado.value = _estado.value?.copy(buscandoCep = false)
                }
            }
        }
    }

    private fun publicarErroCep(erro: ErroCep) {
        ultimoCepBuscado = null
        _estado.value = _estado.value?.copy(erroCep = Evento(erro))
    }

    /**
     * Confere os campos e, se todos estiverem certos, salva o pedido.
     *
     * O que é conferido:
     * - documento: precisa ser CPF ou CNPJ válido (dígitos verificadores certos);
     * - nome: não pode ser vazio;
     * - endereço: CEP com 8 números, rua/número/bairro/cidade preenchidos;
     * - quantidade e distância: números maiores que zero (a vírgula é trocada por ponto, porque o
     *   Kotlin só entende "2.5");
     * - data de entrega: precisa ter sido escolhida.
     *
     * Se algo estiver errado, publica quais campos têm erro e para (nem usa a internet). Se estiver
     * tudo certo, grava no Firestore. Cadastrar precisa de internet na hora; se não houver, a tela
     * mostra "não foi possível salvar" (não existe fila offline para o gerente).
     *
     * O `requireNotNull(x)` diz ao Kotlin "tenho certeza que isto não é nulo aqui" — e é verdade,
     * porque a conferência de cima já garantiu.
     */
    fun salvar(
        documentoDigitado: String,
        nome: String,
        cepDigitado: String,
        rua: String,
        numero: String,
        bairro: String,
        cidade: String,
        quantidadeDigitada: String,
        distanciaDigitada: String,
        dataEntrega: Long?
    ) {
        val documento = DocumentoUtils.apenasDigitos(documentoDigitado)
        val tipoPessoa = DocumentoUtils.tipoPessoa(documento)
        val documentoInvalido = tipoPessoa == null || !DocumentoUtils.documentoValido(documento)
        val nomeInvalido = nome.isEmpty()

        val cep = DocumentoUtils.apenasDigitos(cepDigitado)
        val enderecoInvalido = cep.length != 8 || rua.isEmpty() || numero.isEmpty() ||
            bairro.isEmpty() || cidade.isEmpty()

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
                    // UF chumbada porque a empresa atende somente Santa Catarina
                    uf = "SC",
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
