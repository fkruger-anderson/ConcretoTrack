package br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.PedidoRepositorio
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.PontoGpsRemoto
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.ViagemRepositorio
import kotlinx.coroutines.launch

/** Estado exibido por [RotaViagemActivity][br.com.anderson.meuprimeiroprojeto.gerencial.ui.RotaViagemActivity]. */
data class RotaViagemUiState(
    val pontos: List<PontoGpsRemoto> = emptyList(),
    val carregado: Boolean = false,
    val pedidoId: Int = 0,
    val destinatarioNome: String = "",
    val metrosCubicos: Double = 0.0,
    val dataInicio: Long? = null,
    val dataFim: Long? = null,
    val kmPercorridos: Double? = null
)

/** Busca os pontos de GPS e os dados da viagem/pedido — desenhar a rota é trabalho da View. */
class RotaViagemViewModel : ViewModel() {

    private val _estado = MutableLiveData(RotaViagemUiState())
    val estado: LiveData<RotaViagemUiState> = _estado

    fun carregar(viagemId: String) {
        viewModelScope.launch {
            val pontos = try {
                ViagemRepositorio.listarPontosGps(viagemId)
            } catch (erro: Exception) {
                emptyList()
            }

            val viagem = try {
                ViagemRepositorio.buscarPorId(viagemId)
            } catch (erro: Exception) {
                null
            }
            val pedido = viagem?.let {
                try {
                    PedidoRepositorio.buscarPorId(it.pedidoId)
                } catch (erro: Exception) {
                    null
                }
            }

            _estado.value = RotaViagemUiState(
                pontos = pontos,
                carregado = true,
                pedidoId = viagem?.pedidoId ?: 0,
                destinatarioNome = pedido?.destinatarioNome ?: "",
                metrosCubicos = viagem?.metrosCubicos ?: 0.0,
                dataInicio = viagem?.dataInicio,
                dataFim = viagem?.dataFim,
                kmPercorridos = viagem?.kmPercorridos
            )
        }
    }
}
