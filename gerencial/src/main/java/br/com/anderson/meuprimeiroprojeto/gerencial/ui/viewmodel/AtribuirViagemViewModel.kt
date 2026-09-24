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
import br.com.anderson.meuprimeiroprojeto.gerencial.dados.SaldoPedido
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Os tipos de erro ao atribuir uma viagem. A tela escolhe o texto certo para cada um. */
enum class AtribuirErro { SALDO_INSUFICIENTE, SEM_CONEXAO, GENERICO }

/**
 * O estado da tela de atribuir viagem
 * ([AtribuirViagemActivity][br.com.anderson.meuprimeiroprojeto.gerencial.ui.AtribuirViagemActivity]).
 */
data class AtribuirViagemUiState(
    /** `true` se o pedido não existe (a tela fecha). */
    val pedidoNaoEncontrado: Boolean = false,
    /** Dados do pedido para o cabeçalho: número, destinatário e saldo em m³. */
    val pedidoId: Int = 0,
    val destinatarioNome: String = "",
    val saldo: Double = 0.0,
    /** Opções das caixas de seleção. */
    val motoristas: List<MotoristaComId> = emptyList(),
    val caminhoes: List<Caminhao> = emptyList(),
    /** `true` se não há motorista ou caminhão cadastrado (não dá para atribuir). */
    val semDadosParaAtribuir: Boolean = false,
    /** `true` enquanto a atribuição está sendo enviada. */
    val enviando: Boolean = false,
    /** A quantidade digitada é inválida (vazia, zero, negativa ou não é número). */
    val erroQuantidade: Boolean = false,
    /** Erro vindo do servidor, ou `null`. */
    val erroAtribuir: AtribuirErro? = null,
    /** Aviso de sucesso (a tela fecha), tratado uma vez só. */
    val sucesso: Evento<Unit>? = null
)

/**
 * O "cérebro" da tela de atribuir viagem: carrega o pedido e as listas de motoristas e caminhões,
 * e envia a atribuição.
 *
 * A regra mais importante (não reservar mais m³ do que o saldo) é conferida no servidor, dentro de
 * uma transação ([ViagemRepositorio.atribuir]), porque só lá dá para garantir que duas atribuições
 * ao mesmo tempo não passem juntas. Aqui só se barra o que é obviamente errado antes de gastar
 * internet.
 */
class AtribuirViagemViewModel : ViewModel() {

    companion object {
        // Etiqueta das mensagens desta classe no Logcat.
        private const val TAG = "AtribuirViagemViewModel"

        // Atribuir precisa de conexão na hora (transação no servidor) — sem isso o gerente
        // ficaria esperando pra sempre, então cortamos e mostramos erro de conexão.
        // O tempo máximo de espera é de 10 segundos.
        private const val TEMPO_LIMITE_MS = 10_000L
    }

    // `_estado` é a versão que o ViewModel pode mudar; `estado` é a versão só de leitura da tela.
    private val _estado = MutableLiveData(AtribuirViagemUiState())
    /** O estado atual da tela; a tela é avisada toda vez que ele muda. */
    val estado: LiveData<AtribuirViagemUiState> = _estado

    /**
     * Busca o pedido (para mostrar o saldo) e as listas de motoristas e caminhões (para as caixas de
     * seleção). Se o pedido não existir, avisa a tela. Se uma das listas falhar, ela fica vazia e o
     * erro vai para o Logcat.
     *
     * O saldo mostrado é calculado por [SaldoPedido], a mesma conta que a transação de atribuir usa
     * para aceitar ou recusar. Assim o gerente vê exatamente o valor que pode atribuir (por exemplo
     * 1,1, e não 1,0999999999999996).
     */
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
                saldo = SaldoPedido.calcular(pedido.quantidadeTotalM3, pedido.m3Alocados),
                motoristas = motoristas,
                caminhoes = caminhoes,
                semDadosParaAtribuir = motoristas.isEmpty() || caminhoes.isEmpty()
            )
        }
    }

    /**
     * Confere a quantidade digitada e envia a atribuição.
     *
     * 1. Converte o texto em número (aceita vírgula). Se ficar vazio, não for número ou for menor ou
     *    igual a zero, marca o erro e para.
     * 2. Envia para o servidor com no máximo 10 segundos de espera (`withTimeout`).
     * 3. Trata o resultado: sucesso, saldo insuficiente (outro gerente pode ter reservado antes),
     *    sem conexão, ou erro genérico.
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
