package br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import br.com.anderson.meuprimeiroprojeto.R
import br.com.anderson.meuprimeiroprojeto.motorista.backend.PedidoRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.backend.RoteiroRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.backend.ViagemRemota
import br.com.anderson.meuprimeiroprojeto.motorista.backend.ViagemRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.db.StatusRoteiro
import br.com.anderson.meuprimeiroprojeto.motorista.db.StatusViagem
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Ação disponível no botão da tela de detalhes, conforme o estado da viagem/roteiro. */
enum class AcaoViagem { ADICIONAR_AO_ROTEIRO, REMOVER_DO_ROTEIRO, MARCAR_ENTREGUE, NENHUMA }

/** Estado exibido por [DetalhesViagemActivity][br.com.anderson.meuprimeiroprojeto.motorista.ui.DetalhesViagemActivity]. */
data class DetalhesViagemUiState(
    val carregado: Boolean = false,
    val erroAoCarregar: Boolean = false,
    val destinatarioNome: String = "",
    val enderecoResumido: String = "",
    val metrosCubicos: Double = 0.0,
    val caminhaoPlaca: String = "",
    val status: String = "",
    val roteiroId: String? = null,
    val dataInicio: Long? = null,
    val dataFim: Long? = null,
    val kmPercorridos: Double? = null,
    val acao: AcaoViagem = AcaoViagem.NENHUMA,
    val carregando: Boolean = false,
    val erroAcao: Evento<String>? = null,
    val acaoConcluida: Evento<Unit>? = null
)

/**
 * Carrega os dados da viagem/pedido e decide qual ação o botão oferece — ver [AcaoViagem]. Sem
 * mapa/Room aqui: o trajeto ao vivo e o cálculo de km vivem na aba Roteiro agora.
 */
class DetalhesViagemViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        // Adicionar/remover do roteiro precisa de conexão na hora (lê e valida o roteiro no
        // servidor) — sem isso o motorista ficaria esperando pra sempre.
        private const val TEMPO_LIMITE_MS = 10_000L
    }

    private val _estado = MutableLiveData(DetalhesViagemUiState())
    val estado: LiveData<DetalhesViagemUiState> = _estado

    private lateinit var motoristaUid: String

    fun carregar(viagemId: String, motoristaUid: String) {
        this.motoristaUid = motoristaUid
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

            val acao = decidirAcao(viagem, motoristaUid)

            _estado.value = DetalhesViagemUiState(
                carregado = true,
                destinatarioNome = pedido?.destinatarioNome ?: "",
                enderecoResumido = pedido?.enderecoResumido() ?: "",
                metrosCubicos = viagem.metrosCubicos,
                caminhaoPlaca = viagem.caminhaoPlaca,
                status = viagem.status,
                roteiroId = viagem.roteiroId,
                dataInicio = viagem.dataInicio,
                dataFim = viagem.dataFim,
                kmPercorridos = viagem.kmPercorridos,
                acao = acao
            )
        }
    }

    /**
     * Sem roteiro ainda: oferece "adicionar" só se o motorista não tiver roteiro em rota agora
     * (roteiro só aceita viagem nova enquanto planejando). Com roteiro: "remover" se ainda
     * planejando, "marcar entregue" se em rota e a viagem ainda não foi concluída, nada se já
     * concluída/cancelada.
     */
    private suspend fun decidirAcao(viagem: ViagemRemota, motoristaUid: String): AcaoViagem {
        if (viagem.status == StatusViagem.CONCLUIDA.name || viagem.status == StatusViagem.CANCELADA.name) {
            return AcaoViagem.NENHUMA
        }
        val roteiroId = viagem.roteiroId
        if (roteiroId == null) {
            val roteiroAtivo = try {
                RoteiroRepositorio.buscarRoteiroAtivo(motoristaUid)
            } catch (erro: Exception) {
                null
            }
            return if (roteiroAtivo?.second?.status == StatusRoteiro.EM_ROTA.name) {
                AcaoViagem.NENHUMA
            } else {
                AcaoViagem.ADICIONAR_AO_ROTEIRO
            }
        }
        val roteiro = try {
            RoteiroRepositorio.buscarPorId(roteiroId)
        } catch (erro: Exception) {
            null
        }
        return when (roteiro?.status) {
            StatusRoteiro.PLANEJANDO.name -> AcaoViagem.REMOVER_DO_ROTEIRO
            StatusRoteiro.EM_ROTA.name -> AcaoViagem.MARCAR_ENTREGUE
            else -> AcaoViagem.NENHUMA
        }
    }

    fun adicionarAoRoteiro(viagemId: String) {
        _estado.value = _estado.value?.copy(carregando = true)
        viewModelScope.launch {
            try {
                withTimeout(TEMPO_LIMITE_MS) {
                    RoteiroRepositorio.adicionarViagem(viagemId, motoristaUid)
                }
                _estado.value = _estado.value?.copy(carregando = false, acaoConcluida = Evento(Unit))
            } catch (erro: RoteiroRepositorio.CaminhaoIncompativelException) {
                _estado.value = _estado.value?.copy(carregando = false, erroAcao = Evento(erro.message ?: ""))
            } catch (erro: RoteiroRepositorio.RoteiroJaIniciadoException) {
                _estado.value = _estado.value?.copy(carregando = false, erroAcao = Evento(erro.message ?: ""))
            } catch (erro: TimeoutCancellationException) {
                _estado.value = _estado.value?.copy(carregando = false, erroAcao = Evento(getApplication<Application>().getString(R.string.erro_sem_conexao_tentar_novo)))
            } catch (erro: Exception) {
                _estado.value = _estado.value?.copy(carregando = false, erroAcao = Evento(getApplication<Application>().getString(R.string.erro_sem_conexao_tentar_novo)))
            }
        }
    }

    fun removerDoRoteiro(viagemId: String, roteiroId: String) {
        _estado.value = _estado.value?.copy(carregando = true)
        viewModelScope.launch {
            try {
                withTimeout(TEMPO_LIMITE_MS) {
                    RoteiroRepositorio.removerViagem(viagemId, roteiroId)
                }
                _estado.value = _estado.value?.copy(carregando = false, acaoConcluida = Evento(Unit))
            } catch (erro: RoteiroRepositorio.RoteiroJaIniciadoException) {
                _estado.value = _estado.value?.copy(carregando = false, erroAcao = Evento(erro.message ?: ""))
            } catch (erro: TimeoutCancellationException) {
                _estado.value = _estado.value?.copy(carregando = false, erroAcao = Evento(getApplication<Application>().getString(R.string.erro_sem_conexao_tentar_novo)))
            } catch (erro: Exception) {
                _estado.value = _estado.value?.copy(carregando = false, erroAcao = Evento(getApplication<Application>().getString(R.string.erro_sem_conexao_tentar_novo)))
            }
        }
    }

    /** Confirma a entrega no servidor (transação — ver [RoteiroRepositorio.marcarEntregue]). */
    fun marcarEntregue(viagemId: String) {
        viewModelScope.launch {
            val confirmado = RoteiroRepositorio.marcarEntregue(getApplication(), viagemId)
            _estado.value = if (confirmado) {
                _estado.value?.copy(acaoConcluida = Evento(Unit))
            } else {
                _estado.value?.copy(
                    acaoConcluida = Evento(Unit),
                    erroAcao = Evento(getApplication<Application>().getString(R.string.info_entrega_enfileirada))
                )
            }
        }
    }
}
