package br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.PedidoRepositorio
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.PontoGpsRemoto
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.RoteiroRepositorio
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.ViagemRepositorio
import br.com.anderson.meuprimeiroprojeto.gerencial.dados.StatusViagem
import kotlinx.coroutines.launch

/**
 * Uma linha da tabela de "rateio" (divisão dos km do roteiro entre os pedidos): um pedido do
 * roteiro, com o peso, o percentual e os km que couberam a ele.
 *
 * [kmAtribuido] vazio (`null`) pode querer dizer duas coisas: a viagem ainda não foi resolvida (o
 * roteiro não fechou) ou a viagem foi cancelada (e nunca vai receber km, de propósito). O campo
 * [cancelada] existe só para a tela saber qual das duas mostrar.
 */
data class ItemRateio(
    val viagemId: String,
    val pedidoId: Int,
    val destinatarioNome: String,
    /** m³ desta viagem. */
    val volumeM3: Double,
    /** Distância da usina até a obra do pedido. */
    val distanciaDedicadaKm: Double,
    /** Peso = volume × distância. Quanto maior o peso, maior a fatia dos km. */
    val pesoTeorico: Double,
    /** A fatia deste item, em % do peso total das viagens não canceladas. */
    val percentualRateio: Double,
    /** Os km que foram de fato gravados para esta viagem ao encerrar o roteiro. */
    val kmAtribuido: Double?,
    val cancelada: Boolean,
    /** `true` para a linha que deve aparecer em destaque (a viagem em que o gerente tocou). */
    val destacado: Boolean
)

/**
 * O estado da tela de detalhe do roteiro
 * ([DetalhesRoteiroActivity][br.com.anderson.meuprimeiroprojeto.gerencial.ui.DetalhesRoteiroActivity]).
 */
data class DetalhesRoteiroUiState(
    /** `true` depois que os dados chegaram. */
    val carregado: Boolean = false,
    /** Pontos de GPS do trajeto, para desenhar no mapa. */
    val pontos: List<PontoGpsRemoto> = emptyList(),
    /** Dados do cabeçalho. */
    val motoristaNome: String = "",
    val caminhaoPlaca: String = "",
    val status: String = "",
    val dataInicio: Long? = null,
    val dataFim: Long? = null,
    val kmTotalRota: Double? = null,
    /** As linhas da tabela de rateio. */
    val itens: List<ItemRateio> = emptyList()
)

/**
 * O "cérebro" da tela de detalhe do roteiro: carrega o roteiro, os pontos do trajeto e monta a
 * tabela de rateio de km.
 *
 * Os km de cada viagem já foram calculados e gravados pelo app do motorista, ao encerrar o
 * roteiro (`RoteiroRepositorio.encerrarRoteiro`, no módulo `:app`). Aqui só se LÊ esse valor; o
 * peso e o percentual são recalculados apenas para mostrar na tabela como a divisão foi feita.
 */
class DetalhesRoteiroViewModel : ViewModel() {

    companion object {
        // Etiqueta das mensagens desta classe no Logcat.
        private const val TAG = "DetalhesRoteiroViewModel"
    }

    // `_estado` é a versão que o ViewModel pode mudar; `estado` é a versão só de leitura da tela.
    private val _estado = MutableLiveData(DetalhesRoteiroUiState())
    /** O estado atual da tela; a tela é avisada toda vez que ele muda. */
    val estado: LiveData<DetalhesRoteiroUiState> = _estado

    /**
     * Carrega tudo o que a tela mostra:
     * 1. o roteiro (se não existir, só registra no Logcat e para — a tela fica vazia);
     * 2. os pontos de GPS, para o mapa;
     * 3. as viagens do roteiro e a distância dedicada de cada pedido envolvido;
     * 4. a tabela de rateio: cada viagem ganha um peso (m³ × distância) e um percentual sobre o
     *    peso total das viagens NÃO canceladas.
     *
     * Exemplo: duas viagens de 5 m³, uma para uma obra a 10 km (peso 50) e outra a 30 km (peso 150).
     * Peso total 200: a primeira fica com 25% dos km e a segunda com 75%.
     *
     * [viagemDestacadaId] é a viagem que o gerente tocou no histórico, para a linha dela aparecer
     * em destaque. Se um dado faltar (falha de internet), ele vira lista vazia ou zero, para não
     * travar a tela inteira.
     */
    fun carregar(roteiroId: String, viagemDestacadaId: String?) {
        viewModelScope.launch {
            val roteiro = try {
                RoteiroRepositorio.buscarPorId(roteiroId)
            } catch (erro: Exception) {
                Log.w(TAG, "Falha ao buscar roteiro $roteiroId", erro)
                null
            } ?: return@launch

            val pontos = try {
                RoteiroRepositorio.listarPontosGps(roteiroId)
            } catch (erro: Exception) {
                Log.w(TAG, "Falha ao buscar pontos GPS do roteiro $roteiroId", erro)
                emptyList()
            }

            val viagens = try {
                ViagemRepositorio.listarPorRoteiro(roteiroId)
            } catch (erro: Exception) {
                Log.w(TAG, "Falha ao buscar viagens do roteiro $roteiroId", erro)
                emptyList()
            }

            val distanciasPorPedido = viagens.map { (_, viagem) -> viagem.pedidoId }.distinct()
                .associateWith { id ->
                    try {
                        PedidoRepositorio.buscarPorId(id)?.distanciaDedicadaKm ?: 0.0
                    } catch (erro: Exception) {
                        Log.w(TAG, "Falha ao buscar distância dedicada do pedido $id", erro)
                        0.0
                    }
                }

            val pesoTotal = viagens.sumOf { (_, viagem) ->
                if (viagem.status == StatusViagem.CANCELADA.name) {
                    0.0
                } else {
                    viagem.metrosCubicos * (distanciasPorPedido[viagem.pedidoId] ?: 0.0)
                }
            }

            val itens = viagens.map { (id, viagem) ->
                val pedido = try {
                    PedidoRepositorio.buscarPorId(viagem.pedidoId)
                } catch (erro: Exception) {
                    Log.w(TAG, "Falha ao buscar pedido ${viagem.pedidoId}", erro)
                    null
                }
                val distancia = distanciasPorPedido[viagem.pedidoId] ?: 0.0
                val pesoTeorico = viagem.metrosCubicos * distancia
                val percentual = if (pesoTotal > 0.0 && viagem.status != StatusViagem.CANCELADA.name) {
                    pesoTeorico / pesoTotal * 100.0
                } else {
                    0.0
                }
                ItemRateio(
                    viagemId = id,
                    pedidoId = viagem.pedidoId,
                    destinatarioNome = pedido?.destinatarioNome ?: "",
                    volumeM3 = viagem.metrosCubicos,
                    distanciaDedicadaKm = distancia,
                    pesoTeorico = pesoTeorico,
                    percentualRateio = percentual,
                    kmAtribuido = viagem.kmPercorridos,
                    cancelada = viagem.status == StatusViagem.CANCELADA.name,
                    destacado = id == viagemDestacadaId
                )
            }

            _estado.value = DetalhesRoteiroUiState(
                carregado = true,
                pontos = pontos,
                motoristaNome = roteiro.motoristaNome,
                caminhaoPlaca = roteiro.caminhaoPlaca,
                status = roteiro.status,
                dataInicio = roteiro.dataInicio,
                dataFim = roteiro.dataFim,
                kmTotalRota = roteiro.kmTotalRota,
                itens = itens
            )
        }
    }
}
