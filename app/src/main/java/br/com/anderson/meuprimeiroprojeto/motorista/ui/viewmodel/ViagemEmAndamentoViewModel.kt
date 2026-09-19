package br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import br.com.anderson.meuprimeiroprojeto.motorista.backend.PedidoRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.backend.ViagemRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.db.PontoGpsRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.db.StatusViagem
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

/** Estado exibido por [ViagemEmAndamentoActivity][br.com.anderson.meuprimeiroprojeto.motorista.ui.ViagemEmAndamentoActivity]. */
data class ViagemEmAndamentoUiState(
    val destinatarioNome: String = "",
    val totalPontos: Int = 0,
    val pontosPendentes: Int = 0,
    val cancelamento: Evento<String>? = null
)

/**
 * Carrega os dados do pedido, conta os pontos de GPS gravados localmente (fila offline) e
 * escuta se o gerente cancelou a viagem pelo app dele enquanto o motorista está em rota.
 *
 * É uma [AndroidViewModel] (guarda a [Application], não uma Activity) só porque precisa abrir
 * o banco Room local ([AppDatabase]) para contar os pontos — é a exceção aceita à regra de
 * "ViewModel não conhece Context": a Application não vaza memória porque vive o app inteiro.
 */
class ViagemEmAndamentoViewModel(application: Application) : AndroidViewModel(application) {

    private val _estado = MutableLiveData(ViagemEmAndamentoUiState())
    val estado: LiveData<ViagemEmAndamentoUiState> = _estado

    private var registroCancelamento: ListenerRegistration? = null
    private var jaTratouEncerramento = false

    /** Chamada uma vez, ao abrir a tela: carrega os dados do pedido e liga o listener de cancelamento. */
    fun iniciar(viagemId: String) {
        carregarDadosDoPedido(viagemId)
        atualizarContagemDePontos(viagemId)
        escutarCancelamento(viagemId)
    }

    /** Conta no Room quantos pontos de GPS já foram gravados e quantos ainda não sincronizaram. */
    fun atualizarContagemDePontos(viagemId: String) {
        viewModelScope.launch {
            val (total, pendentes) = PontoGpsRepositorio.contarTotalEPendentes(getApplication(), viagemId)
            _estado.value = _estado.value?.copy(totalPontos = total, pontosPendentes = pendentes)
        }
    }

    /** Busca a viagem e o pedido associado só para exibir o nome do destinatário na tela. */
    private fun carregarDadosDoPedido(viagemId: String) {
        viewModelScope.launch {
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
            _estado.value = _estado.value?.copy(destinatarioNome = pedido?.destinatarioNome ?: "")
        }
    }

    /**
     * O gerente pode cancelar a viagem pelo app dele enquanto o motorista está rodando —
     * sem isso o GPS ficaria gravando pontos de uma viagem que não existe mais pro gerente.
     * Escutar o próprio documento é mais simples que ficar consultando de tempos em tempos.
     */
    private fun escutarCancelamento(viagemId: String) {
        registroCancelamento = ViagemRepositorio.escutarViagem(viagemId) { viagem ->
            if (!jaTratouEncerramento && viagem.status == StatusViagem.CANCELADA.name) {
                jaTratouEncerramento = true
                _estado.value = _estado.value?.copy(cancelamento = Evento(viagem.motivoCancelamento ?: ""))
            }
        }
    }

    /** Mesmo raciocínio de sempre: não espera confirmação do servidor — funciona sem sinal. */
    fun finalizarViagem(viagemId: String) {
        jaTratouEncerramento = true
        ViagemRepositorio.finalizarViagem(viagemId)
    }

    /** Remove o listener do Firestore quando a ViewModel morre, para não vazar memória. */
    override fun onCleared() {
        super.onCleared()
        registroCancelamento?.remove()
    }
}
