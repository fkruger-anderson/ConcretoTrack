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

/** Estado exibido por [PedidosFragment][br.com.anderson.meuprimeiroprojeto.gerencial.ui.PedidosFragment]. */
data class PedidosUiState(
    val pedidosFiltrados: List<PedidoComStatus> = emptyList(),
    val erroAoCarregar: Boolean = false
)

/** Lista os pedidos com o filtro de status escolhido — a filtragem em si é feita aqui, não na View. */
class PedidosViewModel : ViewModel() {

    companion object {
        private const val TAG = "PedidosViewModel"
    }

    private val _estado = MutableLiveData(PedidosUiState())
    val estado: LiveData<PedidosUiState> = _estado

    private var todosPedidos: List<PedidoComStatus> = emptyList()

    // null = "Todos".
    private var filtroAtual: StatusPedido? = null

    /** Busca todos os pedidos no Firestore e reaplica o filtro atual sobre a lista nova. */
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

    /** Troca o filtro de status escolhido no spinner e reaplica sobre a lista já carregada. */
    fun filtrarPor(status: StatusPedido?) {
        filtroAtual = status
        aplicarFiltro()
    }

    /** Filtra [todosPedidos] pelo [filtroAtual] (null = sem filtro, mostra todos) e publica o estado. */
    private fun aplicarFiltro() {
        val filtrados = filtroAtual?.let { status -> todosPedidos.filter { it.status == status } } ?: todosPedidos
        _estado.value = PedidosUiState(pedidosFiltrados = filtrados, erroAoCarregar = false)
    }
}
