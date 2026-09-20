package br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import br.com.anderson.meuprimeiroprojeto.motorista.backend.PedidoRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.backend.RoteiroRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.backend.ViagemRemota
import br.com.anderson.meuprimeiroprojeto.motorista.backend.ViagemRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.db.AppDatabase
import br.com.anderson.meuprimeiroprojeto.motorista.db.PontoGps
import br.com.anderson.meuprimeiroprojeto.motorista.db.PontoGpsRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.db.StatusRoteiro
import br.com.anderson.meuprimeiroprojeto.motorista.db.StatusViagem
import br.com.anderson.meuprimeiroprojeto.motorista.gps.RastreamentoService
import kotlinx.coroutines.launch

/** Uma viagem do roteiro atual, já com o nome do destinatário resolvido. */
data class ViagemDoRoteiro(val viagemId: String, val viagem: ViagemRemota, val destinatarioNome: String)

/** Estado exibido por [RoteiroFragment][br.com.anderson.meuprimeiroprojeto.motorista.ui.RoteiroFragment]. */
data class RoteiroUiState(
    val carregado: Boolean = false,
    val temRoteiro: Boolean = false,
    val roteiroId: String? = null,
    val status: String = "",
    val viagens: List<ViagemDoRoteiro> = emptyList(),
    val pontos: List<PontoGps> = emptyList(),
    val podeIniciar: Boolean = false,
    val podeEncerrar: Boolean = false,
    val erro: Evento<String>? = null,
    val roteiroEncerrado: Evento<Unit>? = null
)

/**
 * Estado do roteiro atual do motorista. É [AndroidViewModel] porque precisa do Room (pontos de
 * GPS) e do Context pra chamar o [RastreamentoService].
 */
class RoteiroViewModel(application: Application) : AndroidViewModel(application) {

    private val _estado = MutableLiveData(RoteiroUiState())
    val estado: LiveData<RoteiroUiState> = _estado

    private lateinit var motoristaUid: String

    /** Busca o roteiro ativo do motorista (planejando ou em rota) e, se em rota, os pontos de GPS. */
    fun carregar(motoristaUid: String) {
        this.motoristaUid = motoristaUid
        viewModelScope.launch {
            val roteiroAtivo = try {
                RoteiroRepositorio.buscarRoteiroAtivo(motoristaUid)
            } catch (erro: Exception) {
                null
            }
            if (roteiroAtivo == null) {
                _estado.value = RoteiroUiState(carregado = true, temRoteiro = false)
                return@launch
            }
            val (roteiroId, roteiro) = roteiroAtivo

            val viagensBrutas = try {
                ViagemRepositorio.buscarPorRoteiro(roteiroId)
            } catch (erro: Exception) {
                emptyList()
            }
            val viagens = viagensBrutas.map { (id, viagem) ->
                val pedido = try {
                    PedidoRepositorio.buscarPorId(viagem.pedidoId)
                } catch (erro: Exception) {
                    null
                }
                ViagemDoRoteiro(id, viagem, pedido?.destinatarioNome ?: "")
            }

            val emRota = roteiro.status == StatusRoteiro.EM_ROTA.name
            val pontos = if (emRota) {
                AppDatabase.obter(getApplication()).pontoGpsDao().buscarPorRoteiro(roteiroId)
            } else {
                emptyList()
            }

            val todasResolvidas = viagens.all { (_, viagem, _) ->
                viagem.status == StatusViagem.CONCLUIDA.name || viagem.status == StatusViagem.CANCELADA.name
            }

            _estado.value = RoteiroUiState(
                carregado = true,
                temRoteiro = true,
                roteiroId = roteiroId,
                status = roteiro.status,
                viagens = viagens,
                pontos = pontos,
                podeIniciar = roteiro.status == StatusRoteiro.PLANEJANDO.name && viagens.isNotEmpty(),
                podeEncerrar = emRota && todasResolvidas
            )
        }
    }

    /** Não espera confirmação do servidor — mesma escrita simples usada em todo o app motorista. */
    fun iniciarRoteiro() {
        val estado = _estado.value ?: return
        val roteiroId = estado.roteiroId ?: return
        RoteiroRepositorio.iniciarRoteiro(roteiroId, estado.viagens.map { it.viagemId })
        RastreamentoService.iniciar(getApplication(), roteiroId)
        carregar(motoristaUid)
    }

    /** Não espera confirmação do servidor — mesmo raciocínio de sempre. */
    fun marcarEntregue(viagemId: String) {
        RoteiroRepositorio.marcarEntregue(viagemId)
        carregar(motoristaUid)
    }

    /**
     * Calcula o km total (Haversine sobre os pontos do roteiro), busca a distância dedicada de
     * cada pedido envolvido, grava o rateio e encerra o roteiro. Só deve ser chamado quando
     * [RoteiroUiState.podeEncerrar] for true (todas as viagens já resolvidas).
     */
    fun encerrarRoteiro() {
        val roteiroId = _estado.value?.roteiroId ?: return
        viewModelScope.launch {
            try {
                val viagens = ViagemRepositorio.buscarPorRoteiro(roteiroId)
                val pedidoIds = viagens.map { (_, viagem) -> viagem.pedidoId }.distinct()
                val distancias = pedidoIds.associateWith { id ->
                    try {
                        PedidoRepositorio.buscarPorId(id)?.distanciaDedicadaKm ?: 0.0
                    } catch (erro: Exception) {
                        0.0
                    }
                }
                val km = PontoGpsRepositorio.calcularKmPercorridos(getApplication(), roteiroId)
                RoteiroRepositorio.encerrarRoteiro(roteiroId, km, viagens, distancias)
                RastreamentoService.encerrar(getApplication(), roteiroId)
                _estado.value = _estado.value?.copy(roteiroEncerrado = Evento(Unit))
            } catch (erro: Exception) {
                _estado.value = _estado.value?.copy(erro = Evento("Sem conexão. Tente encerrar de novo"))
            }
        }
    }
}
