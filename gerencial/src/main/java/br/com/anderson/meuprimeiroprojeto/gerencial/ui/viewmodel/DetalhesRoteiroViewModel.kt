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

/** Uma linha da tabela de rateio: um pedido do roteiro, com peso/percentual/km já calculados. */
data class ItemRateio(
    val viagemId: String,
    val pedidoId: Int,
    val destinatarioNome: String,
    val volumeM3: Double,
    val distanciaDedicadaKm: Double,
    val pesoTeorico: Double,
    val percentualRateio: Double,
    val kmAtribuido: Double?,
    val destacado: Boolean
)

/** Estado exibido por [DetalhesRoteiroActivity][br.com.anderson.meuprimeiroprojeto.gerencial.ui.DetalhesRoteiroActivity]. */
data class DetalhesRoteiroUiState(
    val carregado: Boolean = false,
    val pontos: List<PontoGpsRemoto> = emptyList(),
    val motoristaNome: String = "",
    val caminhaoPlaca: String = "",
    val status: String = "",
    val dataInicio: Long? = null,
    val dataFim: Long? = null,
    val kmTotalRota: Double? = null,
    val itens: List<ItemRateio> = emptyList()
)

/**
 * Carrega o roteiro, seus pontos de GPS e a tabela de rateio (peso teórico e percentual
 * recalculados aqui pra exibição; o `kmAtribuido` real de cada viagem já foi gravado por
 * `RoteiroRepositorio.encerrarRoteiro`, no app motorista, ao encerrar — aqui só se lê).
 */
class DetalhesRoteiroViewModel : ViewModel() {

    companion object {
        private const val TAG = "DetalhesRoteiroViewModel"
    }

    private val _estado = MutableLiveData(DetalhesRoteiroUiState())
    val estado: LiveData<DetalhesRoteiroUiState> = _estado

    /**
     * Carrega o roteiro, os pontos de GPS registrados nele e monta a tabela de rateio: cada
     * viagem do roteiro ganha um "peso teórico" (m³ da viagem × distância dedicada do pedido) e
     * um percentual sobre o peso total das viagens não canceladas — é assim que o km total
     * percorrido pelo caminhão nessa rota é dividido entre os pedidos atendidos. Se o roteiro não
     * for encontrado, a função só loga e sai sem atualizar o estado (a tela fica com
     * `carregado = false`). Falhas ao buscar pontos, viagens ou um pedido individual são logadas
     * e tratadas com listas vazias/zero, pra um dado faltando não travar a tela inteira.
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
