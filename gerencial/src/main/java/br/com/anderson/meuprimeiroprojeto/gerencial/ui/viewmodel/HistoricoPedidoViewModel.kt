package br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.PedidoRepositorio
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.ViagemRemota
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.ViagemRepositorio
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Os três jeitos de terminar uma tentativa de cancelamento — a Activity escolhe o toast certo. */
enum class ResultadoCancelamento { SUCESSO, ESTADO_INVALIDO, ERRO_GENERICO }

/** Estado exibido por [HistoricoPedidoActivity][br.com.anderson.meuprimeiroprojeto.gerencial.ui.HistoricoPedidoActivity]. */
data class HistoricoPedidoUiState(
    val pedidoNaoEncontrado: Boolean = false,
    val pedidoId: Int = 0,
    val destinatarioNome: String = "",
    val m3Alocados: Double = 0.0,
    val quantidadeTotalM3: Double = 0.0,
    val saldo: Double = 0.0,
    val viagens: List<Pair<String, ViagemRemota>> = emptyList(),
    val erroAoCarregar: Boolean = false,
    val resultadoCancelamento: Evento<ResultadoCancelamento>? = null
)

/** Histórico de viagens de um pedido: carrega o progresso do saldo e permite cancelar viagens. */
class HistoricoPedidoViewModel : ViewModel() {

    companion object {
        // Cancelar precisa de conexão na hora (transação no servidor) — sem isso o gerente
        // ficaria esperando pra sempre, então cortamos e mostramos erro genérico.
        private const val TEMPO_LIMITE_MS = 10_000L
    }

    private val _estado = MutableLiveData(HistoricoPedidoUiState())
    val estado: LiveData<HistoricoPedidoUiState> = _estado

    /** Busca o pedido (progresso/saldo) e a lista de viagens associadas a ele. */
    fun carregar(pedidoId: Int) {
        viewModelScope.launch {
            try {
                val pedido = PedidoRepositorio.buscarPorId(pedidoId)
                if (pedido == null) {
                    _estado.value = _estado.value?.copy(pedidoNaoEncontrado = true)
                    return@launch
                }

                val viagens = ViagemRepositorio.listarPorPedido(pedidoId)
                _estado.value = HistoricoPedidoUiState(
                    pedidoId = pedidoId,
                    destinatarioNome = pedido.destinatarioNome,
                    m3Alocados = pedido.m3Alocados,
                    quantidadeTotalM3 = pedido.quantidadeTotalM3,
                    saldo = pedido.quantidadeTotalM3 - pedido.m3Alocados,
                    viagens = viagens
                )
            } catch (erro: Exception) {
                _estado.value = _estado.value?.copy(erroAoCarregar = true)
            }
        }
    }

    /**
     * Cancela a viagem (devolvendo os m³ ao saldo do pedido, no servidor) e recarrega a tela.
     * Recarrega mesmo em [ViagemRepositorio.EstadoInvalidoException] (ex.: já foi concluída por
     * outro caminho) pra tela não ficar mostrando um estado desatualizado.
     */
    fun cancelar(viagemId: String, motivo: String, pedidoId: Int) {
        viewModelScope.launch {
            try {
                withTimeout(TEMPO_LIMITE_MS) {
                    ViagemRepositorio.cancelar(viagemId, motivo)
                }
                _estado.value = _estado.value?.copy(resultadoCancelamento = Evento(ResultadoCancelamento.SUCESSO))
                carregar(pedidoId)
            } catch (erro: ViagemRepositorio.EstadoInvalidoException) {
                _estado.value =
                    _estado.value?.copy(resultadoCancelamento = Evento(ResultadoCancelamento.ESTADO_INVALIDO))
                carregar(pedidoId)
            } catch (erro: TimeoutCancellationException) {
                _estado.value = _estado.value?.copy(resultadoCancelamento = Evento(ResultadoCancelamento.ERRO_GENERICO))
            } catch (erro: Exception) {
                _estado.value = _estado.value?.copy(resultadoCancelamento = Evento(ResultadoCancelamento.ERRO_GENERICO))
            }
        }
    }
}
