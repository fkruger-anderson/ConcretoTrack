package br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.RoteiroRepositorio
import com.google.firebase.firestore.ListenerRegistration

/** Um caminhão com roteiro em rota agora — só os dados, sem nada de mapa/Marker aqui. */
data class CaminhaoEmRota(
    val roteiroId: String,
    val latitude: Double,
    val longitude: Double,
    val motoristaNome: String,
    val caminhaoPlaca: String = ""
)

/** Estado exibido por [MapaFragment][br.com.anderson.meuprimeiroprojeto.gerencial.ui.MapaFragment]. */
data class MapaUiState(val caminhoes: List<CaminhaoEmRota> = emptyList())

/**
 * Escuta [RoteiroRepositorio.escutarRoteirosEmRota] e expõe a lista de caminhões atualizados.
 * Desenhar isso no mapa (criar/mover Marker, animar câmera) é trabalho da View — um `Marker` só
 * existe junto com o `GoogleMap`, então não faz sentido a ViewModel guardar isso.
 */
class MapaViewModel : ViewModel() {

    private val _estado = MutableLiveData(MapaUiState())
    val estado: LiveData<MapaUiState> = _estado

    private var registroListener: ListenerRegistration? = null

    /** Começa a escutar os roteiros em rota; chamadas repetidas (ex.: giro de tela) não recriam o listener. */
    fun escutar() {
        if (registroListener != null) return

        registroListener = RoteiroRepositorio.escutarRoteirosEmRota { roteiros ->
            val caminhoes = roteiros.mapNotNull { (roteiroId, roteiro) ->
                val lat = roteiro.ultimaLatitude ?: return@mapNotNull null
                val lng = roteiro.ultimaLongitude ?: return@mapNotNull null
                CaminhaoEmRota(
                    roteiroId = roteiroId,
                    latitude = lat,
                    longitude = lng,
                    motoristaNome = roteiro.motoristaNome,
                    caminhaoPlaca = roteiro.caminhaoPlaca
                )
            }
            _estado.value = MapaUiState(caminhoes)
        }
    }

    /** ViewModel sendo destruída de vez (não é só giro de tela): encerra o listener do Firestore. */
    override fun onCleared() {
        super.onCleared()
        registroListener?.remove()
    }
}
