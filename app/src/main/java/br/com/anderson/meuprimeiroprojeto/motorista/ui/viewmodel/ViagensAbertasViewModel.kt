package br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.anderson.meuprimeiroprojeto.motorista.backend.AutenticacaoRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.backend.PedidoRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.backend.ViagemRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.dados.ViagemAbertaComPedido
import br.com.anderson.meuprimeiroprojeto.motorista.sessao.SessaoMotorista
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
 * e junta cada uma com os dados do pedido dela. Também cuida da saudação e do logout. A tela só
 * observa o [estado], desenha e navega — ela não fala com repositório nenhum.
 *
 * É um `ViewModel` comum (sem `Context`): só conversa com os repositórios e com a [SessaoMotorista].
 */
class ViagensAbertasViewModel : ViewModel() {

    companion object {
        // Etiqueta das mensagens desta classe no Logcat do Android Studio.
        private const val TAG = "ViagensAbertasViewModel"
    }

    // `_estado` é a versão que o ViewModel pode mudar; `estado` é a versão só de leitura da tela.
    private val _estado = MutableLiveData(ViagensAbertasUiState())
    /** O estado atual da aba; a tela é avisada toda vez que ele muda. */
    val estado: LiveData<ViagensAbertasUiState> = _estado

    /** O nome do motorista guardado no celular no login, para a saudação "Olá, fulano". */
    fun nomeMotorista(): String = SessaoMotorista.nomeMotorista()

    /**
     * Busca as viagens do motorista logado e, uma por uma, o pedido de cada viagem. Se ninguém
     * estiver logado, não faz nada.
     *
     * Buscar os pedidos um por um é mais simples de entender do que uma consulta combinada, e a
     * lista é pequena, então não pesa. Se o pedido de uma viagem falhar, a viagem aparece mesmo
     * assim, só sem nome e endereço. Se a busca das viagens falhar, a tela mostra a mensagem de
     * erro. As falhas ficam registradas no Logcat.
     */
    fun carregar() {
        val motoristaUid = AutenticacaoRepositorio.usuarioAtual?.uid ?: return
        viewModelScope.launch {
            try {
                val abertas = ViagemRepositorio.buscarAbertasDoMotorista(motoristaUid)
                val comPedido = abertas.map { (viagemId, viagem) ->
                    val pedido = try {
                        PedidoRepositorio.buscarPorId(viagem.pedidoId)
                    } catch (erro: Exception) {
                        Log.w(TAG, "Falha ao buscar o pedido ${viagem.pedidoId}", erro)
                        null
                    }
                    ViagemAbertaComPedido(viagemId, viagem, pedido)
                }
                _estado.value = ViagensAbertasUiState(viagens = comPedido, erroAoCarregar = false)
            } catch (erro: Exception) {
                Log.w(TAG, "Falha ao carregar as viagens do motorista", erro)
                _estado.value = ViagensAbertasUiState(erroAoCarregar = true)
            }
        }
    }

    /**
     * Faz o logout: o Firebase Auth esquece o usuário e o nome guardado no celular é apagado. Quem
     * leva o motorista de volta para a tela de login é a tela (navegar é trabalho dela).
     */
    fun sair() {
        AutenticacaoRepositorio.sair()
        SessaoMotorista.sair()
    }
}
