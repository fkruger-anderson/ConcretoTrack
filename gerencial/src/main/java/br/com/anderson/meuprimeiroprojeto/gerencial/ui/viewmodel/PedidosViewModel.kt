package br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.PedidoRepositorio
import br.com.anderson.meuprimeiroprojeto.gerencial.dados.PedidoComStatus
import br.com.anderson.meuprimeiroprojeto.gerencial.dados.StatusPedido
import kotlinx.coroutines.launch

/** O estado da aba Pedidos ([PedidosFragment][br.com.anderson.meuprimeiroprojeto.gerencial.ui.PedidosFragment]). */
data class PedidosUiState(
    /** Os pedidos a mostrar, já com o filtro de situação aplicado. */
    val pedidosFiltrados: List<PedidoComStatus> = emptyList(),
    /** `true` se não foi possível buscar os pedidos. */
    val erroAoCarregar: Boolean = false
)

/**
 * O "cérebro" da aba Pedidos: busca todos os pedidos e aplica o filtro de situação escolhido na
 * caixa de seleção (Todos, Pendente, Em andamento, Concluído).
 *
 * A filtragem é feita aqui, e não na tela: a tela só desenha o que recebe.
 */
class PedidosViewModel : ViewModel() {

    companion object {
        // Etiqueta das mensagens desta classe no Logcat do Android Studio.
        private const val TAG = "PedidosViewModel"
    }

    // `_estado` é a versão que o ViewModel pode mudar; `estado` é a versão só de leitura da tela.
    private val _estado = MutableLiveData(PedidosUiState())
    /** O estado atual da aba; a tela é avisada toda vez que ele muda. */
    val estado: LiveData<PedidosUiState> = _estado

    // A lista completa, guardada para poder trocar o filtro sem buscar tudo de novo na internet.
    private var todosPedidos: List<PedidoComStatus> = emptyList()

    // null = "Todos".
    private var filtroAtual: StatusPedido? = null

    /**
     * Busca todos os pedidos no Firestore e aplica o filtro atual sobre a lista nova. Se der erro,
     * registra no Logcat (`Log.w`, "w" de warning = aviso) e publica o erro para a tela.
     */
    fun carregar() {
        viewModelScope.launch {
            try {
                todosPedidos = PedidoRepositorio.listarComStatus()
                aplicarFiltro()
            } catch (erro: Exception) {
                Log.w(TAG, "Falha ao carregar pedidos", erro)
                _estado.value = PedidosUiState(erroAoCarregar = true)
            }
        }
    }

    /** Troca o filtro (quando o gerente escolhe outra opção) e aplica sobre a lista já carregada. */
    fun filtrarPor(status: StatusPedido?) {
        filtroAtual = status
        aplicarFiltro()
    }

    /**
     * Fica só com os pedidos da situação escolhida (ou todos, se o filtro for `null`) e publica o
     * resultado. `?.let { ... } ?: todosPedidos` quer dizer: "se houver filtro, filtra; se não
     * houver, usa a lista inteira".
     */
    private fun aplicarFiltro() {
        val filtrados = filtroAtual?.let { status -> todosPedidos.filter { it.status == status } } ?: todosPedidos
        _estado.value = PedidosUiState(pedidosFiltrados = filtrados, erroAoCarregar = false)
    }
}
