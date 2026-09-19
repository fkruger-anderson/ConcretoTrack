package br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.anderson.meuprimeiroprojeto.motorista.backend.PedidoRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.backend.ViagemRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.dados.ViagemAtribuidaComPedido
import kotlinx.coroutines.launch

/** Estado exibido por [MinhasViagensActivity][br.com.anderson.meuprimeiroprojeto.motorista.ui.MinhasViagensActivity]. */
data class MinhasViagensUiState(
    val viagens: List<ViagemAtribuidaComPedido> = emptyList(),
    val erroAoCarregar: Boolean = false,
    val viagemEmAndamento: ViagemEmAndamentoResumo? = null
)

/** Dados mínimos pra mostrar o botão "viagem em andamento — toque para continuar". */
data class ViagemEmAndamentoResumo(
    val viagemId: String,
    val descricaoPedido: String
)

/**
 * Busca as viagens atribuídas ao motorista (ainda não iniciadas) e verifica se há uma viagem
 * em rota que ele deixou pra trás sem finalizar. A Activity só observa [estado] e desenha a tela.
 */
class MinhasViagensViewModel : ViewModel() {

    private val _estado = MutableLiveData(MinhasViagensUiState())
    val estado: LiveData<MinhasViagensUiState> = _estado

    /** Dispara as duas buscas independentes: viagens atribuídas e viagem em rota pendente. */
    fun carregar(motoristaUid: String) {
        carregarViagensAtribuidas(motoristaUid)
        verificarViagemEmAndamento(motoristaUid)
    }

    /** Busca as viagens atribuídas (não iniciadas) e junta cada uma com os dados do seu pedido. */
    private fun carregarViagensAtribuidas(motoristaUid: String) {
        viewModelScope.launch {
            try {
                val atribuidas = ViagemRepositorio.buscarAtribuidasDoMotorista(motoristaUid)
                val comPedido = atribuidas.map { (viagemId, viagem) ->
                    val pedido = try {
                        PedidoRepositorio.buscarPorId(viagem.pedidoId)
                    } catch (erro: Exception) {
                        null
                    }
                    ViagemAtribuidaComPedido(viagemId, viagem, pedido)
                }
                _estado.value = _estado.value?.copy(viagens = comPedido, erroAoCarregar = false)
            } catch (erro: Exception) {
                _estado.value = _estado.value?.copy(erroAoCarregar = true)
            }
        }
    }

    /**
     * Verifica se o motorista tem alguma viagem em rota (status EM_ROTA) que não finalizou —
     * por exemplo, se fechou o app no meio do caminho — para mostrar o botão de continuar.
     */
    private fun verificarViagemEmAndamento(motoristaUid: String) {
        viewModelScope.launch {
            val encontrada = try {
                ViagemRepositorio.buscarEmRotaDoMotorista(motoristaUid)
            } catch (erro: Exception) {
                null
            }
            if (encontrada == null) {
                _estado.value = _estado.value?.copy(viagemEmAndamento = null)
                return@launch
            }

            val (viagemId, viagem) = encontrada
            val pedido = try {
                PedidoRepositorio.buscarPorId(viagem.pedidoId)
            } catch (erro: Exception) {
                null
            }
            _estado.value = _estado.value?.copy(
                viagemEmAndamento = ViagemEmAndamentoResumo(
                    viagemId = viagemId,
                    descricaoPedido = pedido?.destinatarioNome ?: viagem.pedidoId.toString()
                )
            )
        }
    }
}
