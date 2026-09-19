package br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.CaminhaoRepositorio
import kotlinx.coroutines.launch

/** Erros que só o servidor pode detectar (os de campo ficam em [CadastroCaminhaoUiState] à parte). */
enum class CadastroCaminhaoErro { PLACA_DUPLICADA, GENERICO }

/** Estado exibido por [CadastroCaminhaoActivity][br.com.anderson.meuprimeiroprojeto.gerencial.ui.CadastroCaminhaoActivity]. */
data class CadastroCaminhaoUiState(
    val carregando: Boolean = false,
    val erroCampoPlaca: Boolean = false,
    val erroMarcaModelo: Boolean = false,
    val erroCampoAno: Boolean = false,
    val erroGeral: CadastroCaminhaoErro? = null,
    val sucesso: Evento<Unit>? = null
)

/** Valida os campos e cadastra o caminhão (placa, marca, modelo, ano) no Firestore. */
class CadastroCaminhaoViewModel : ViewModel() {

    companion object {
        // Cobre placa antiga (LLL-DDDD) e Mercosul (LLL-D-L-DD): a 5ª posição pode ser letra ou número.
        private val REGEX_PLACA = Regex("^[A-Z]{3}[0-9][A-Z0-9][0-9]{2}$")
    }

    private val _estado = MutableLiveData(CadastroCaminhaoUiState())
    val estado: LiveData<CadastroCaminhaoUiState> = _estado

    /** Valida placa, marca/modelo e ano; se tudo ok, cadastra e trata a placa duplicada como erro específico. */
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
