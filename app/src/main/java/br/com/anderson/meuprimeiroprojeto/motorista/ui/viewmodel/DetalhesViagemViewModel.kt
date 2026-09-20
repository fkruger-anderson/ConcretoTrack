package br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import br.com.anderson.meuprimeiroprojeto.motorista.backend.PedidoRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.backend.ViagemRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.db.AppDatabase
import br.com.anderson.meuprimeiroprojeto.motorista.db.PontoGps
import br.com.anderson.meuprimeiroprojeto.motorista.db.PontoGpsRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.db.StatusViagem
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

/** Estado exibido por [DetalhesViagemActivity][br.com.anderson.meuprimeiroprojeto.motorista.ui.DetalhesViagemActivity]. */
data class DetalhesViagemUiState(
    val carregado: Boolean = false,
    val erroAoCarregar: Boolean = false,
    val destinatarioNome: String = "",
    val enderecoResumido: String = "",
    val metrosCubicos: Double = 0.0,
    val caminhaoPlaca: String = "",
    val status: String = "",
    val dataInicio: Long? = null,
    val dataFim: Long? = null,
    val kmPercorridos: Double = 0.0,
    val pontos: List<PontoGps> = emptyList(),
    val cancelamento: Evento<String>? = null,
    val encerrada: Evento<Unit>? = null
)

/**
 * Carrega os dados da viagem/pedido; se a viagem estiver em rota, também carrega os pontos de
 * GPS (pra desenhar o trajeto) e o km já percorrido, e escuta cancelamento remoto do gerente.
 * É [AndroidViewModel] porque precisa do Room (pontos de GPS, cálculo de km).
 */
class DetalhesViagemViewModel(application: Application) : AndroidViewModel(application) {

    private val _estado = MutableLiveData(DetalhesViagemUiState())
    val estado: LiveData<DetalhesViagemUiState> = _estado

    private var registroCancelamento: ListenerRegistration? = null
    private var jaTratouEncerramento = false

    /** Busca a viagem/pedido; se em rota, também os pontos de GPS e o km, e liga a escuta de cancelamento. */
    fun carregar(viagemId: String) {
        viewModelScope.launch {
            val viagem = try {
                ViagemRepositorio.buscarPorId(viagemId)
            } catch (erro: Exception) {
                null
            }
            if (viagem == null) {
                _estado.value = _estado.value?.copy(erroAoCarregar = true)
                return@launch
            }

            val pedido = try {
                PedidoRepositorio.buscarPorId(viagem.pedidoId)
            } catch (erro: Exception) {
                null
            }

            val emRota = viagem.status == StatusViagem.EM_ROTA.name
            val pontos = if (emRota) {
                AppDatabase.obter(getApplication()).pontoGpsDao().buscarPorViagem(viagemId)
            } else {
                emptyList()
            }
            val km = if (emRota) PontoGpsRepositorio.calcularKmPercorridos(getApplication(), viagemId) else 0.0

            _estado.value = DetalhesViagemUiState(
                carregado = true,
                destinatarioNome = pedido?.destinatarioNome ?: "",
                enderecoResumido = pedido?.enderecoResumido() ?: "",
                metrosCubicos = viagem.metrosCubicos,
                caminhaoPlaca = viagem.caminhaoPlaca,
                status = viagem.status,
                dataInicio = viagem.dataInicio,
                dataFim = viagem.dataFim,
                kmPercorridos = km,
                pontos = pontos
            )

            if (emRota) escutarCancelamento(viagemId)
        }
    }

    /** Não espera confirmação do servidor — funciona sem sinal (mesmo raciocínio de sempre). */
    fun confirmarInicio(viagemId: String) {
        ViagemRepositorio.iniciarTransito(viagemId)
        carregar(viagemId)
    }

    /**
     * Calcula o km final a partir dos pontos locais e grava junto com status/dataFim, numa
     * escrita só. Guarda simétrica à de [escutarCancelamento]: se o gerente já cancelou a
     * viagem remotamente antes deste toque chegar aqui, não faz nada — senão sobrescreveria o
     * cancelamento com CONCLUIDA.
     */
    fun confirmarEncerramento(viagemId: String) {
        if (jaTratouEncerramento) return
        jaTratouEncerramento = true
        viewModelScope.launch {
            val km = PontoGpsRepositorio.calcularKmPercorridos(getApplication(), viagemId)
            ViagemRepositorio.finalizarViagem(viagemId, km)
            _estado.value = _estado.value?.copy(encerrada = Evento(Unit))
        }
    }

    /**
     * O gerente pode cancelar a viagem pelo app dele enquanto o motorista está rodando. Ao
     * detectar, calcula e grava o km já percorrido (o caminhão rodou aquela distância de
     * verdade), mesmo a viagem ficando cancelada.
     */
    private fun escutarCancelamento(viagemId: String) {
        if (registroCancelamento != null) return
        registroCancelamento = ViagemRepositorio.escutarViagem(viagemId) { viagem ->
            if (!jaTratouEncerramento && viagem.status == StatusViagem.CANCELADA.name) {
                jaTratouEncerramento = true
                viewModelScope.launch {
                    val km = PontoGpsRepositorio.calcularKmPercorridos(getApplication(), viagemId)
                    ViagemRepositorio.gravarKmPercorridos(viagemId, km)
                    _estado.value = _estado.value?.copy(cancelamento = Evento(viagem.motivoCancelamento ?: ""))
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        registroCancelamento?.remove()
    }
}
