package br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.PedidoRepositorio
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.ViagemRemota
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.ViagemRepositorio
import br.com.anderson.meuprimeiroprojeto.gerencial.dados.SaldoPedido
import br.com.anderson.meuprimeiroprojeto.gerencial.dados.StatusPedido
import br.com.anderson.meuprimeiroprojeto.gerencial.dados.StatusViagem
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Os resultados possíveis de uma tentativa de cancelar viagem. A tela mostra o aviso certo para cada um. */
enum class ResultadoCancelamento { SUCESSO, ESTADO_INVALIDO, ERRO_GENERICO }

/**
 * O estado da tela de histórico do pedido
 * ([HistoricoPedidoActivity][br.com.anderson.meuprimeiroprojeto.gerencial.ui.HistoricoPedidoActivity]).
 */
data class HistoricoPedidoUiState(
    /** `true` se o pedido não existe (a tela fecha). */
    val pedidoNaoEncontrado: Boolean = false,
    /** Dados do cabeçalho: número, destinatário, m³ reservados, total, saldo e data de entrega. */
    val pedidoId: Int = 0,
    val destinatarioNome: String = "",
    val m3Alocados: Double = 0.0,
    val quantidadeTotalM3: Double = 0.0,
    val saldo: Double = 0.0,
    val dataEntrega: Long = 0,
    /** Situação calculada do pedido. */
    val status: StatusPedido = StatusPedido.PENDENTE,
    /** Soma dos km de todas as viagens não canceladas do pedido. */
    val totalKm: Double = 0.0,
    /** As viagens do pedido, cada uma como par (id, dados). */
    val viagens: List<Pair<String, ViagemRemota>> = emptyList(),
    /** `true` se não foi possível carregar. */
    val erroAoCarregar: Boolean = false,
    /** Resultado do último cancelamento, tratado uma vez só. */
    val resultadoCancelamento: Evento<ResultadoCancelamento>? = null,
    /** Aviso de erro ao trocar a data de entrega, tratado uma vez só. */
    val erroAtualizarDataEntrega: Evento<Unit>? = null
)

/**
 * O "cérebro" da tela de histórico do pedido: mostra o progresso do saldo e as viagens do pedido,
 * e permite cancelar viagens e trocar a data de entrega.
 */
class HistoricoPedidoViewModel : ViewModel() {

    companion object {
        // Cancelar precisa de conexão na hora (transação no servidor) — sem isso o gerente
        // ficaria esperando pra sempre, então cortamos e mostramos erro genérico.
        // O tempo máximo de espera é de 10 segundos.
        private const val TEMPO_LIMITE_MS = 10_000L
    }

    // `_estado` é a versão que o ViewModel pode mudar; `estado` é a versão só de leitura da tela.
    private val _estado = MutableLiveData(HistoricoPedidoUiState())
    /** O estado atual da tela; a tela é avisada toda vez que ele muda. */
    val estado: LiveData<HistoricoPedidoUiState> = _estado

    /**
     * Busca o pedido e as viagens dele, e calcula:
     * - o saldo, por [SaldoPedido] (arredondado para o litro). É dele que depende o botão
     *   "Atribuir viagem": com a conta sem arredondar, sobrava um saldo minúsculo e o botão
     *   continuava aparecendo num pedido já todo atribuído;
     * - a situação do pedido, reaproveitando a lista de viagens já carregada para saber se há
     *   viagem pendente (sem fazer outra consulta);
     * - o total de km, somando as viagens que não foram canceladas (`?: 0.0` trata como zero as que
     *   ainda não receberam km).
     */
    fun carregar(pedidoId: Int) {
        viewModelScope.launch {
            try {
                val pedido = PedidoRepositorio.buscarPorId(pedidoId)
                if (pedido == null) {
                    _estado.value = _estado.value?.copy(pedidoNaoEncontrado = true)
                    return@launch
                }

                val viagens = ViagemRepositorio.listarPorPedido(pedidoId)
                // Reaproveita a lista de viagens já carregada pra saber se há alguma pendente,
                // em vez de fazer outra consulta ao Firestore (como PedidoRepositorio.listarComStatus faz).
                val temViagemPendente = viagens.any { (_, viagem) ->
                    viagem.status == StatusViagem.ATRIBUIDA.name || viagem.status == StatusViagem.EM_ROTA.name
                }
                val status = PedidoRepositorio.calcularStatus(pedido.m3Alocados, pedido.quantidadeTotalM3, temViagemPendente)

                val totalKm = viagens
                    .filter { (_, viagem) -> viagem.status != StatusViagem.CANCELADA.name }
                    .sumOf { (_, viagem) -> viagem.kmPercorridos ?: 0.0 }

                _estado.value = HistoricoPedidoUiState(
                    pedidoId = pedidoId,
                    destinatarioNome = pedido.destinatarioNome,
                    m3Alocados = pedido.m3Alocados,
                    quantidadeTotalM3 = pedido.quantidadeTotalM3,
                    saldo = SaldoPedido.calcular(pedido.quantidadeTotalM3, pedido.m3Alocados),
                    dataEntrega = pedido.dataEntrega,
                    status = status,
                    totalKm = totalKm,
                    viagens = viagens
                )
            } catch (erro: Exception) {
                _estado.value = _estado.value?.copy(erroAoCarregar = true)
            }
        }
    }

    /**
     * Cancela a viagem (no servidor, o que devolve os m³ ao saldo do pedido) e recarrega a tela.
     *
     * Também recarrega quando a viagem já não podia ser cancelada ([ViagemRepositorio.EstadoInvalidoException],
     * por exemplo porque o motorista acabou de entregar): assim a tela para de mostrar a situação
     * antiga. Com no máximo 10 segundos de espera.
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

    /**
     * Troca a data de entrega do pedido e recarrega a tela para mostrar a data nova.
     *
     * Pedido concluído não pode mais mudar de data. O botão já fica desligado na tela nesse caso,
     * mas a conferência aqui protege caso a tela esteja com um estado desatualizado.
     */
    fun atualizarDataEntrega(pedidoId: Int, novaData: Long) {
        if (_estado.value?.status == StatusPedido.CONCLUIDO) return

        viewModelScope.launch {
            try {
                PedidoRepositorio.atualizarDataEntrega(pedidoId, novaData)
                carregar(pedidoId)
            } catch (erro: Exception) {
                _estado.value = _estado.value?.copy(erroAtualizarDataEntrega = Evento(Unit))
            }
        }
    }
}
