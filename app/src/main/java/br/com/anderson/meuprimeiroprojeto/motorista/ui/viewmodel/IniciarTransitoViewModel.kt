package br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.anderson.meuprimeiroprojeto.motorista.backend.PedidoRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.backend.ViagemRepositorio
import kotlinx.coroutines.launch

/** Estado exibido por [IniciarTransitoActivity][br.com.anderson.meuprimeiroprojeto.motorista.ui.IniciarTransitoActivity]. */
data class IniciarTransitoUiState(
    val viagem: DetalhesViagemParaIniciar? = null,
    val erroAoCarregar: Boolean = false
)

/** Dados da viagem atribuída — nada aqui é editável pelo motorista, só confirma o início. */
data class DetalhesViagemParaIniciar(
    val destinatarioNome: String,
    val enderecoResumido: String,
    val metrosCubicos: Double,
    val caminhaoPlaca: String
)

/**
 * Carrega os dados da viagem (pedido + endereço) que o gerente atribuiu, e confirma o início
 * do trânsito quando o motorista tocar no botão.
 */
class IniciarTransitoViewModel : ViewModel() {

    private val _estado = MutableLiveData(IniciarTransitoUiState())
    val estado: LiveData<IniciarTransitoUiState> = _estado

    /** Busca a viagem e o pedido associado no Firestore para montar [DetalhesViagemParaIniciar]. */
    fun carregar(viagemId: String) {
        viewModelScope.launch {
            val viagem = try {
                ViagemRepositorio.buscarPorId(viagemId)
            } catch (erro: Exception) {
                null
            }
            if (viagem == null) {
                _estado.value = _estado.value?.copy(erroAoCarregar = true)
                return@launch
            }

            val pedido = try {
                PedidoRepositorio.buscarPorId(viagem.pedidoId)
            } catch (erro: Exception) {
                null
            }
            _estado.value = _estado.value?.copy(
                viagem = DetalhesViagemParaIniciar(
                    destinatarioNome = pedido?.destinatarioNome ?: "",
                    enderecoResumido = pedido?.enderecoResumido() ?: "",
                    metrosCubicos = viagem.metrosCubicos,
                    caminhaoPlaca = viagem.caminhaoPlaca
                ),
                erroAoCarregar = false
            )
        }
    }

    /**
     * Não espera confirmação do servidor — funciona mesmo sem sinal (ver o comentário em
     * [ViagemRepositorio.iniciarTransito]). Por isso não é suspend: a Activity já navega pra
     * próxima tela na sequência, sem esperar a ViewModel terminar nada.
     */
    fun iniciarTransito(viagemId: String) {
        ViagemRepositorio.iniciarTransito(viagemId)
    }
}
