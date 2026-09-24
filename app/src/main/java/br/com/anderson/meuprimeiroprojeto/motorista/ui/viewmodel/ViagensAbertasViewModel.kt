package br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.anderson.meuprimeiroprojeto.motorista.backend.PedidoRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.backend.ViagemRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.dados.ViagemAbertaComPedido
import kotlinx.coroutines.launch

/**
 * O estado da aba Viagens ([ViagensFragment][br.com.anderson.meuprimeiroprojeto.motorista.ui.ViagensFragment]):
 * tudo o que a tela precisa para se desenhar.
 */
data class ViagensAbertasUiState(
    /** As viagens a mostrar, cada uma já com os dados do seu pedido. */
    val viagens: List<ViagemAbertaComPedido> = emptyList(),
    /** `true` se não foi possível buscar a lista (por exemplo, sem internet). */
    val erroAoCarregar: Boolean = false
)

/**
 * O "cérebro" da aba Viagens: busca as viagens do motorista que ainda não estão em nenhum roteiro
 * e junta cada uma com os dados do pedido dela. A tela só observa o [estado] e desenha a lista.
 *
 * É um `ViewModel` comum (não precisa de `Context`): só conversa com os repositórios.
 */
class ViagensAbertasViewModel : ViewModel() {

    // `_estado` é a versão que o ViewModel pode mudar; `estado` é a versão só de leitura da tela.
    private val _estado = MutableLiveData(ViagensAbertasUiState())
    /** O estado atual da aba; a tela é avisada toda vez que ele muda. */
    val estado: LiveData<ViagensAbertasUiState> = _estado

    /**
     * Busca as viagens do motorista e, uma por uma, o pedido de cada viagem.
     *
     * Buscar os pedidos um por um é mais simples de entender do que uma consulta combinada, e a
     * lista é pequena, então não pesa. Se o pedido de uma viagem falhar, a viagem aparece mesmo
     * assim, só sem nome e endereço. Se a busca das viagens falhar, a tela mostra a mensagem de
     * erro.
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
