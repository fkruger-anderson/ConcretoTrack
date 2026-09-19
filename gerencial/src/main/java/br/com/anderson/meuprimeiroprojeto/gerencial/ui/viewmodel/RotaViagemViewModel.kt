package br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.PontoGpsRemoto
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.ViagemRepositorio
import kotlinx.coroutines.launch

/** Estado exibido por [RotaViagemActivity][br.com.anderson.meuprimeiroprojeto.gerencial.ui.RotaViagemActivity]. */
data class RotaViagemUiState(
    val pontos: List<PontoGpsRemoto> = emptyList(),
    val carregado: Boolean = false
)

/** Busca os pontos de GPS da viagem — desenhar a rota (Polyline/Marker) é trabalho da View. */
class RotaViagemViewModel : ViewModel() {

    private val _estado = MutableLiveData(RotaViagemUiState())
    val estado: LiveData<RotaViagemUiState> = _estado

    /** Busca os pontos de GPS da viagem no Firestore; lista vazia em caso de erro ou sem pontos. */
    fun carregar(viagemId: String) {
        viewModelScope.launch {
            val pontos = try {
                ViagemRepositorio.listarPontosGps(viagemId)
            } catch (erro: Exception) {
                emptyList()
            }
            _estado.value = RotaViagemUiState(pontos = pontos, carregado = true)
        }
    }
}
