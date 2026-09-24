package br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.RoteiroRepositorio
import com.google.firebase.firestore.ListenerRegistration

/**
 * Um caminhão na rua agora (um roteiro em rota), só com os dados que o mapa precisa. Não tem nada
 * do Google Maps aqui: desenhar é trabalho da tela.
 */
data class CaminhaoEmRota(
    /** Id do roteiro; serve para saber qual marcador do mapa é deste caminhão. */
    val roteiroId: String,
    /** Última posição conhecida. */
    val latitude: Double,
    val longitude: Double,
    /** Para o texto do marcador. */
    val motoristaNome: String,
    val caminhaoPlaca: String = ""
)

/** O estado da aba Mapa ([MapaFragment][br.com.anderson.meuprimeiroprojeto.gerencial.ui.MapaFragment]). */
data class MapaUiState(val caminhoes: List<CaminhaoEmRota> = emptyList())

/**
 * O "cérebro" da aba Mapa: fica escutando no Firestore os roteiros em rota (ver
 * [RoteiroRepositorio.escutarRoteirosEmRota]) e publica a lista de caminhões com a posição mais
 * recente. Toda vez que um caminhão anda, a lista é publicada de novo e o mapa se atualiza.
 *
 * Criar e mover os marcadores (`Marker`) no mapa é trabalho da tela: um marcador só existe junto
 * com o mapa, que é parte da tela, então não faria sentido o ViewModel guardá-lo.
 */
class MapaViewModel : ViewModel() {

    // `_estado` é a versão que o ViewModel pode mudar; `estado` é a versão só de leitura da tela.
    private val _estado = MutableLiveData(MapaUiState())
    /** A lista atual de caminhões em rota; a tela é avisada toda vez que ela muda. */
    val estado: LiveData<MapaUiState> = _estado

    // O "controle remoto" do ouvinte do Firestore, para poder desligá-lo depois.
    private var registroListener: ListenerRegistration? = null

    /**
     * Liga o ouvinte do Firestore. Se ele já estiver ligado (por exemplo, a tela foi recriada ao
     * girar o celular, mas o ViewModel é o mesmo), não liga outro.
     *
     * A cada atualização, transforma cada roteiro em [CaminhaoEmRota]. Roteiros que ainda não
     * mandaram nenhuma posição (latitude ou longitude vazias) ficam de fora — `mapNotNull` descarta
     * os itens que viraram `null`.
     */
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

    /**
     * Chamado pelo Android quando o ViewModel vai ser destruído de vez (a tela foi fechada, e não
     * só girada). Desliga o ouvinte do Firestore, para ele não continuar recebendo dados à toa.
     */
    override fun onCleared() {
        super.onCleared()
        registroListener?.remove()
    }
}
