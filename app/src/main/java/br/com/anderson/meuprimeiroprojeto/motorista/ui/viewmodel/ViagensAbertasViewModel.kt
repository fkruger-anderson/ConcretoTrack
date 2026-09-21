package br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.anderson.meuprimeiroprojeto.motorista.backend.PedidoRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.backend.ViagemRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.dados.ViagemAbertaComPedido
import kotlinx.coroutines.launch

/** Estado exibido por [ViagensAbertasActivity][br.com.anderson.meuprimeiroprojeto.motorista.ui.ViagensAbertasActivity]. */
data class ViagensAbertasUiState(
    val viagens: List<ViagemAbertaComPedido> = emptyList(),
    val erroAoCarregar: Boolean = false
)

/**
 * Busca as viagens em aberto do motorista (atribuídas ou em rota) e junta cada uma com os
 * dados do seu pedido. A Activity só observa [estado] e desenha a lista.
 */
class ViagensAbertasViewModel : ViewModel() {

    private val _estado = MutableLiveData(ViagensAbertasUiState())
    val estado: LiveData<ViagensAbertasUiState> = _estado

    /**
     * Busca as viagens em aberto do motorista e, uma a uma, o pedido correspondente de cada
     * viagem (não é uma junção no servidor — mais simples de entender, e o volume de dados aqui
     * é pequeno). Se um pedido específico falhar ao buscar, a viagem entra na lista mesmo assim,
     * só sem os dados do pedido.
     */
    fun carregar(motoristaUid: String) {
        viewModelScope.launch {
            try {
                val abertas = ViagemRepositorio.buscarAbertasDoMotorista(motoristaUid)
                val comPedido = abertas.map { (viagemId, viagem) ->
                    val pedido = try {
                        PedidoRepositorio.buscarPorId(viagem.pedidoId)
                    } catch (erro: Exception) {
                        null
                    }
                    ViagemAbertaComPedido(viagemId, viagem, pedido)
                }
                _estado.value = ViagensAbertasUiState(viagens = comPedido, erroAoCarregar = false)
            } catch (erro: Exception) {
                _estado.value = ViagensAbertasUiState(erroAoCarregar = true)
            }
        }
    }
}
