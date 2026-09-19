package br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.ViagemRepositorio
import com.google.firebase.firestore.ListenerRegistration

/** Um caminhão com viagem em rota agora — só os dados, sem nada de mapa/Marker aqui. */
data class CaminhaoEmRota(
    val viagemId: String,
    val latitude: Double,
    val longitude: Double,
    val motoristaNome: String,
    val caminhaoPlaca: String
)

/** Estado exibido por [MapaFragment][br.com.anderson.meuprimeiroprojeto.gerencial.ui.MapaFragment]. */
data class MapaUiState(val caminhoes: List<CaminhaoEmRota> = emptyList())

/**
 * Escuta [ViagemRepositorio.escutarViagensEmRota] e expõe a lista de caminhões atualizados.
 * Desenhar isso no mapa (criar/mover Marker, animar câmera) é trabalho da View — um `Marker` só
 * existe junto com o `GoogleMap`, então não faz sentido a ViewModel guardar isso. O que ganha
 * ao sair da Fragment pra cá é só a inscrição no Firestore, que agora sobrevive ao giro de tela
 * em vez de reinscrever do zero toda vez que a view é recriada.
 */
class MapaViewModel : ViewModel() {

    private val _estado = MutableLiveData(MapaUiState())
    val estado: LiveData<MapaUiState> = _estado

    private var registroListener: ListenerRegistration? = null

    /** Começa a escutar as viagens em rota; chamadas repetidas (ex.: giro de tela) não recriam o listener. */
    fun escutar() {
        if (registroListener != null) return

        registroListener = ViagemRepositorio.escutarViagensEmRota { viagens ->
            val caminhoes = viagens.mapNotNull { (viagemId, viagem) ->
                val lat = viagem.ultimaLatitude ?: return@mapNotNull null
                val lng = viagem.ultimaLongitude ?: return@mapNotNull null
                CaminhaoEmRota(
                    viagemId = viagemId,
                    latitude = lat,
                    longitude = lng,
                    motoristaNome = viagem.motoristaNome,
                    caminhaoPlaca = viagem.caminhaoPlaca
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
