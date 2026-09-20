package br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.CaminhaoRepositorio
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.MotoristaComId
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.MotoristaRepositorio
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.PedidoRepositorio
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.ViagemRepositorio
import br.com.anderson.meuprimeiroprojeto.gerencial.dados.Caminhao
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Os jeitos de dar errado ao atribuir — a Activity escolhe o texto certo pra cada um. */
enum class AtribuirErro { SALDO_INSUFICIENTE, SEM_CONEXAO, GENERICO }

/** Estado exibido por [AtribuirViagemActivity][br.com.anderson.meuprimeiroprojeto.gerencial.ui.AtribuirViagemActivity]. */
data class AtribuirViagemUiState(
    val pedidoNaoEncontrado: Boolean = false,
    val pedidoId: Int = 0,
    val destinatarioNome: String = "",
    val saldo: Double = 0.0,
    val motoristas: List<MotoristaComId> = emptyList(),
    val caminhoes: List<Caminhao> = emptyList(),
    val semDadosParaAtribuir: Boolean = false,
    val enviando: Boolean = false,
    val erroQuantidade: Boolean = false,
    val erroAtribuir: AtribuirErro? = null,
    val sucesso: Evento<Unit>? = null
)

/**
 * Carrega o pedido (com saldo) e as listas de motoristas/caminhões cadastrados, e atribui a
 * viagem — a validação de saldo é feita em transação no servidor ([ViagemRepositorio.atribuir]),
 * então aqui só se valida a quantidade ser um número maior que zero antes de enviar.
 */
class AtribuirViagemViewModel : ViewModel() {

    companion object {
        private const val TAG = "AtribuirViagemViewModel"

        // Atribuir precisa de conexão na hora (transação no servidor) — sem isso o gerente
        // ficaria esperando pra sempre, então cortamos e mostramos erro de conexão.
        private const val TEMPO_LIMITE_MS = 10_000L
    }

    private val _estado = MutableLiveData(AtribuirViagemUiState())
    val estado: LiveData<AtribuirViagemUiState> = _estado

    /** Busca o pedido (pro saldo) e as listas de motoristas/caminhões cadastrados para os spinners. */
    fun carregar(pedidoId: Int) {
        viewModelScope.launch {
            val pedido = try {
                PedidoRepositorio.buscarPorId(pedidoId)
            } catch (erro: Exception) {
                Log.w(TAG, "Falha ao buscar pedido $pedidoId", erro)
                null
            }
            if (pedido == null) {
                _estado.value = _estado.value?.copy(pedidoNaoEncontrado = true)
                return@launch
            }

            val motoristas = try {
                MotoristaRepositorio.listar()
            } catch (erro: Exception) {
                Log.w(TAG, "Falha ao listar motoristas", erro)
                emptyList()
            }
            val caminhoes = try {
                CaminhaoRepositorio.listar()
            } catch (erro: Exception) {
                Log.w(TAG, "Falha ao listar caminhões", erro)
                emptyList()
            }

            _estado.value = AtribuirViagemUiState(
                pedidoId = pedidoId,
                destinatarioNome = pedido.destinatarioNome,
                saldo = pedido.quantidadeTotalM3 - pedido.m3Alocados,
                motoristas = motoristas,
                caminhoes = caminhoes,
                semDadosParaAtribuir = motoristas.isEmpty() || caminhoes.isEmpty()
            )
        }
    }

    /**
     * Valida a quantidade digitada e envia a atribuição ao repositório. A validação de saldo em
     * si (quantidade <= saldo) é feita em transação no servidor, então aqui só barramos entradas
     * obviamente inválidas (vazio, não numérico, <= 0) antes de gastar uma chamada de rede.
     */
    fun atribuir(pedidoId: Int, motorista: MotoristaComId, caminhao: Caminhao, quantidadeDigitada: String) {
        val quantidadeM3 = quantidadeDigitada.trim().replace(',', '.').toDoubleOrNull()
        if (quantidadeM3 == null || quantidadeM3 <= 0.0) {
            _estado.value = _estado.value?.copy(erroQuantidade = true)
            return
        }

        _estado.value = _estado.value?.copy(enviando = true, erroQuantidade = false, erroAtribuir = null)
        viewModelScope.launch {
            try {
                withTimeout(TEMPO_LIMITE_MS) {
                    ViagemRepositorio.atribuir(
                        pedidoId = pedidoId,
                        motoristaUid = motorista.uid,
                        motoristaNome = motorista.perfil.nome,
                        motoristaCpf = motorista.perfil.cpf,
                        caminhaoPlaca = caminhao.placa,
                        metrosCubicos = quantidadeM3
                    )
                }
                _estado.value = _estado.value?.copy(enviando = false, sucesso = Evento(Unit))
            } catch (erro: ViagemRepositorio.SaldoInsuficienteException) {
                _estado.value = _estado.value?.copy(enviando = false, erroAtribuir = AtribuirErro.SALDO_INSUFICIENTE)
            } catch (erro: TimeoutCancellationException) {
                _estado.value = _estado.value?.copy(enviando = false, erroAtribuir = AtribuirErro.SEM_CONEXAO)
            } catch (erro: Exception) {
                Log.w(TAG, "Falha ao atribuir viagem do pedido $pedidoId", erro)
                _estado.value = _estado.value?.copy(enviando = false, erroAtribuir = AtribuirErro.GENERICO)
            }
        }
    }
}
