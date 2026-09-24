package br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.CaminhaoRepositorio
import kotlinx.coroutines.launch

/**
 * Erros que só o servidor consegue descobrir (placa repetida, falha de internet). Os erros de
 * preenchimento de cada campo ficam à parte, em [CadastroCaminhaoUiState].
 */
enum class CadastroCaminhaoErro { PLACA_DUPLICADA, GENERICO }

/**
 * O estado da tela de cadastro de caminhão
 * ([CadastroCaminhaoActivity][br.com.anderson.meuprimeiroprojeto.gerencial.ui.CadastroCaminhaoActivity]).
 */
data class CadastroCaminhaoUiState(
    /** `true` enquanto o cadastro está sendo enviado. */
    val carregando: Boolean = false,
    /** Erros de preenchimento: placa, marca/modelo e ano. */
    val erroCampoPlaca: Boolean = false,
    val erroMarcaModelo: Boolean = false,
    val erroCampoAno: Boolean = false,
    /** Erro vindo do servidor, ou `null`. */
    val erroGeral: CadastroCaminhaoErro? = null,
    /** Aviso de sucesso (a tela fecha), tratado uma vez só. */
    val sucesso: Evento<Unit>? = null
)

/** O "cérebro" da tela de cadastro de caminhão: confere os campos e cadastra no Firestore. */
class CadastroCaminhaoViewModel : ViewModel() {

    companion object {
        // Cobre placa antiga (LLL-DDDD) e Mercosul (LLL-D-L-DD): a 5ª posição pode ser letra ou número.
        // Lendo a "expressão regular": 3 letras, 1 número, 1 letra ou número, 2 números.
        private val REGEX_PLACA = Regex("^[A-Z]{3}[0-9][A-Z0-9][0-9]{2}$")
    }

    // `_estado` é a versão que o ViewModel pode mudar; `estado` é a versão só de leitura da tela.
    private val _estado = MutableLiveData(CadastroCaminhaoUiState())
    /** O estado atual da tela; a tela é avisada toda vez que ele muda. */
    val estado: LiveData<CadastroCaminhaoUiState> = _estado

    /**
     * Confere os campos e, se estiverem certos, cadastra o caminhão.
     *
     * - Placa: passa para maiúsculas e confere o formato com [REGEX_PLACA].
     * - Marca e modelo: não podem ser vazios.
     * - Ano: precisa ser um número entre 1970 e 2100.
     *
     * Se algo estiver errado, publica quais campos têm erro e para. Se estiver tudo certo, envia ao
     * Firestore; uma placa já cadastrada vira o erro [CadastroCaminhaoErro.PLACA_DUPLICADA].
     */
    fun criar(placaDigitada: String, marcaDigitada: String, modeloDigitado: String, anoDigitado: String) {
        val placa = placaDigitada.trim().uppercase()
        val placaInvalida = !REGEX_PLACA.matches(placa)

        val marca = marcaDigitada.trim()
        val modelo = modeloDigitado.trim()
        val marcaModeloInvalido = marca.isEmpty() || modelo.isEmpty()

        val ano = anoDigitado.trim().toIntOrNull()
        val anoInvalido = ano == null || ano < 1970 || ano > 2100

        if (placaInvalida || marcaModeloInvalido || anoInvalido) {
            _estado.value = _estado.value?.copy(
                erroCampoPlaca = placaInvalida,
                erroMarcaModelo = marcaModeloInvalido,
                erroCampoAno = anoInvalido,
                erroGeral = null
            )
            return
        }

        _estado.value = _estado.value?.copy(
            carregando = true,
            erroCampoPlaca = false,
            erroMarcaModelo = false,
            erroCampoAno = false,
            erroGeral = null
        )
        viewModelScope.launch {
            try {
                CaminhaoRepositorio.cadastrar(placa, marca, modelo, requireNotNull(ano))
                _estado.value = _estado.value?.copy(carregando = false, sucesso = Evento(Unit))
            } catch (erro: CaminhaoRepositorio.PlacaJaCadastradaException) {
                _estado.value =
                    _estado.value?.copy(carregando = false, erroGeral = CadastroCaminhaoErro.PLACA_DUPLICADA)
            } catch (erro: Exception) {
                _estado.value = _estado.value?.copy(carregando = false, erroGeral = CadastroCaminhaoErro.GENERICO)
            }
        }
    }
}
