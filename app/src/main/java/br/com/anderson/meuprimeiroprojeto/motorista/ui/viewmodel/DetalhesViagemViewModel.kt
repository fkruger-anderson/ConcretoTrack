package br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.anderson.meuprimeiroprojeto.motorista.backend.AutenticacaoRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.backend.PedidoRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.backend.RoteiroRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.backend.ViagemRemota
import br.com.anderson.meuprimeiroprojeto.motorista.backend.ViagemRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.db.StatusRoteiro
import br.com.anderson.meuprimeiroprojeto.motorista.db.StatusViagem
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** As ações que o botão da tela de detalhes pode oferecer (ou nenhuma). */
enum class AcaoViagem { ADICIONAR_AO_ROTEIRO, REMOVER_DO_ROTEIRO, MARCAR_ENTREGUE, NENHUMA }

/**
 * Os avisos que a tela de detalhes pode mostrar depois de uma ação. A tela escolhe o texto de cada
 * um no `strings.xml` — o ViewModel não sabe nada de textos (assim como no app gerencial).
 */
enum class AvisoViagem {
    /** A viagem é de um caminhão diferente das que já estão no roteiro. */
    CAMINHAO_DIFERENTE,
    /** O roteiro já saiu da usina, então não aceita mais mudanças. */
    ROTEIRO_JA_INICIADO,
    /** Sem internet (ou o servidor não respondeu a tempo). */
    SEM_CONEXAO,
    /** A entrega foi marcada sem sinal e será enviada sozinha quando a conexão voltar. */
    ENTREGA_ENFILEIRADA
}

/**
 * O estado da tela de detalhes da viagem
 * ([DetalhesViagemActivity][br.com.anderson.meuprimeiroprojeto.motorista.ui.DetalhesViagemActivity]):
 * tudo o que ela precisa para se desenhar.
 */
data class DetalhesViagemUiState(
    /** `true` depois que os dados chegaram (antes disso a tela não desenha nada). */
    val carregado: Boolean = false,
    /** `true` se a viagem não foi encontrada. */
    val erroAoCarregar: Boolean = false,
    /** Dados para mostrar: destinatário, endereço, m³ e placa. */
    val destinatarioNome: String = "",
    val enderecoResumido: String = "",
    val metrosCubicos: Double = 0.0,
    val caminhaoPlaca: String = "",
    /** Situação da viagem (nome de `StatusViagem`). */
    val status: String = "",
    /** Roteiro em que a viagem está, se estiver em algum. */
    val roteiroId: String? = null,
    /** Datas de início e fim, e a parte dos km que coube a esta viagem (se já calculada). */
    val dataInicio: Long? = null,
    val dataFim: Long? = null,
    val kmPercorridos: Double? = null,
    /** Qual ação o botão deve mostrar. */
    val acao: AcaoViagem = AcaoViagem.NENHUMA,
    /** `true` enquanto uma ação está em andamento (o botão fica desligado). */
    val carregando: Boolean = false,
    /** Aviso para mostrar num Toast, uma vez só. */
    val aviso: Evento<AvisoViagem>? = null,
    /** Aviso de "ação concluída" (a tela fecha), uma vez só. */
    val acaoConcluida: Evento<Unit>? = null
)

/**
 * O "cérebro" da tela de detalhes da viagem: carrega os dados da viagem e do pedido, decide qual
 * ação o botão oferece (ver [AcaoViagem]) e executa essa ação.
 *
 * É um `ViewModel` comum, sem `Context` (regra da disciplina): os avisos são um `enum` que a tela
 * transforma em texto, e o banco local usado ao marcar entrega sem sinal já foi preparado quando o
 * app começou a rodar.
 */
class DetalhesViagemViewModel : ViewModel() {

    companion object {
        // Etiqueta das mensagens desta classe no Logcat do Android Studio.
        private const val TAG = "DetalhesViagemViewModel"

        // Adicionar/remover do roteiro precisa de conexão na hora (lê e valida o roteiro no
        // servidor) — sem isso o motorista ficaria esperando pra sempre.
        // Por isso existe um tempo máximo de espera: 10 segundos.
        private const val TEMPO_LIMITE_MS = 10_000L
    }

    // `_estado` é a versão que o ViewModel pode mudar; `estado` é a versão só de leitura da tela.
    private val _estado = MutableLiveData(DetalhesViagemUiState())
    /** O estado atual da tela; a tela é avisada toda vez que ele muda. */
    val estado: LiveData<DetalhesViagemUiState> = _estado

    // Guardado no carregar() para ser usado depois, ao adicionar a viagem ao roteiro.
    private lateinit var motoristaUid: String

    /**
     * Busca a viagem e o pedido dela, decide a ação do botão ([decidirAcao]) e publica tudo de uma
     * vez no [estado]. Se ninguém estiver logado, não faz nada; se a viagem não for encontrada,
     * publica o erro.
     *
     * O `try { ... } catch { null }` é um jeito curto de dizer "se der erro, use null" (o erro fica
     * registrado no Logcat).
     */
    fun carregar(viagemId: String) {
        motoristaUid = AutenticacaoRepositorio.usuarioAtual?.uid ?: return
        viewModelScope.launch {
            val viagem = try {
                ViagemRepositorio.buscarPorId(viagemId)
            } catch (erro: Exception) {
                Log.w(TAG, "Falha ao buscar a viagem $viagemId", erro)
                null
            }
            if (viagem == null) {
                _estado.value = _estado.value?.copy(erroAoCarregar = true)
                return@launch
            }

            val pedido = try {
                PedidoRepositorio.buscarPorId(viagem.pedidoId)
            } catch (erro: Exception) {
                Log.w(TAG, "Falha ao buscar o pedido ${viagem.pedidoId}", erro)
                null
            }

            val acao = decidirAcao(viagem, motoristaUid)

            _estado.value = DetalhesViagemUiState(
                carregado = true,
                destinatarioNome = pedido?.destinatarioNome ?: "",
                enderecoResumido = pedido?.enderecoResumido() ?: "",
                metrosCubicos = viagem.metrosCubicos,
                caminhaoPlaca = viagem.caminhaoPlaca,
                status = viagem.status,
                roteiroId = viagem.roteiroId,
                dataInicio = viagem.dataInicio,
                dataFim = viagem.dataFim,
                kmPercorridos = viagem.kmPercorridos,
                acao = acao
            )
        }
    }

    /**
     * Decide qual botão mostrar, seguindo as regras do roteiro:
     * - viagem já entregue ou cancelada: nenhum botão;
     * - viagem sem roteiro: "Adicionar", a não ser que o motorista já tenha um roteiro EM ROTA
     *   (um roteiro que já saiu não aceita viagem nova);
     * - viagem num roteiro PLANEJANDO: "Remover";
     * - viagem num roteiro EM ROTA: "Marcar entregue";
     * - qualquer outro caso (por exemplo, roteiro não encontrado): nenhum botão.
     */
    private suspend fun decidirAcao(viagem: ViagemRemota, motoristaUid: String): AcaoViagem {
        if (viagem.status == StatusViagem.CONCLUIDA.name || viagem.status == StatusViagem.CANCELADA.name) {
            return AcaoViagem.NENHUMA
        }
        val roteiroId = viagem.roteiroId
        if (roteiroId == null) {
            val roteiroAtivo = try {
                RoteiroRepositorio.buscarRoteiroAtivo(motoristaUid)
            } catch (erro: Exception) {
                Log.w(TAG, "Falha ao buscar o roteiro ativo do motorista", erro)
                null
            }
            return if (roteiroAtivo?.second?.status == StatusRoteiro.EM_ROTA.name) {
                AcaoViagem.NENHUMA
            } else {
                AcaoViagem.ADICIONAR_AO_ROTEIRO
            }
        }
        val roteiro = try {
            RoteiroRepositorio.buscarPorId(roteiroId)
        } catch (erro: Exception) {
            Log.w(TAG, "Falha ao buscar o roteiro $roteiroId", erro)
            null
        }
        return when (roteiro?.status) {
            StatusRoteiro.PLANEJANDO.name -> AcaoViagem.REMOVER_DO_ROTEIRO
            StatusRoteiro.EM_ROTA.name -> AcaoViagem.MARCAR_ENTREGUE
            else -> AcaoViagem.NENHUMA
        }
    }

    /**
     * Coloca a viagem no roteiro do motorista. Precisa de internet na hora (o servidor confere o
     * roteiro antes), então usa `withTimeout`: se não houver resposta em 10 segundos, desiste e
     * mostra um aviso, em vez de deixar o motorista esperando para sempre.
     *
     * Cada `catch` transforma um tipo de erro num [AvisoViagem]: caminhão diferente, roteiro já
     * iniciado, ou sem conexão (tempo esgotado ou qualquer outra falha, que vai para o Logcat).
     */
    fun adicionarAoRoteiro(viagemId: String) {
        _estado.value = _estado.value?.copy(carregando = true)
        viewModelScope.launch {
            try {
                withTimeout(TEMPO_LIMITE_MS) {
                    RoteiroRepositorio.adicionarViagem(viagemId, motoristaUid)
                }
                _estado.value = _estado.value?.copy(carregando = false, acaoConcluida = Evento(Unit))
            } catch (erro: RoteiroRepositorio.CaminhaoIncompativelException) {
                _estado.value = _estado.value?.copy(carregando = false, aviso = Evento(AvisoViagem.CAMINHAO_DIFERENTE))
            } catch (erro: RoteiroRepositorio.RoteiroJaIniciadoException) {
                _estado.value = _estado.value?.copy(carregando = false, aviso = Evento(AvisoViagem.ROTEIRO_JA_INICIADO))
            } catch (erro: TimeoutCancellationException) {
                _estado.value = _estado.value?.copy(carregando = false, aviso = Evento(AvisoViagem.SEM_CONEXAO))
            } catch (erro: Exception) {
                Log.w(TAG, "Falha ao adicionar a viagem $viagemId ao roteiro", erro)
                _estado.value = _estado.value?.copy(carregando = false, aviso = Evento(AvisoViagem.SEM_CONEXAO))
            }
        }
    }

    /**
     * Tira a viagem do roteiro (que ainda está sendo planejado). Mesma ideia de tempo máximo de
     * [adicionarAoRoteiro]: sem resposta em 10 segundos, desiste e mostra o aviso.
     */
    fun removerDoRoteiro(viagemId: String, roteiroId: String) {
        _estado.value = _estado.value?.copy(carregando = true)
        viewModelScope.launch {
            try {
                withTimeout(TEMPO_LIMITE_MS) {
                    RoteiroRepositorio.removerViagem(viagemId, roteiroId)
                }
                _estado.value = _estado.value?.copy(carregando = false, acaoConcluida = Evento(Unit))
            } catch (erro: RoteiroRepositorio.RoteiroJaIniciadoException) {
                _estado.value = _estado.value?.copy(carregando = false, aviso = Evento(AvisoViagem.ROTEIRO_JA_INICIADO))
            } catch (erro: TimeoutCancellationException) {
                _estado.value = _estado.value?.copy(carregando = false, aviso = Evento(AvisoViagem.SEM_CONEXAO))
            } catch (erro: Exception) {
                Log.w(TAG, "Falha ao remover a viagem $viagemId do roteiro", erro)
                _estado.value = _estado.value?.copy(carregando = false, aviso = Evento(AvisoViagem.SEM_CONEXAO))
            }
        }
    }

    /**
     * Marca a viagem como entregue (ver [RoteiroRepositorio.marcarEntregue]). Nos dois casos a
     * tela fecha; se a confirmação ficou guardada no celular para enviar depois (sem sinal), mostra
     * também um aviso explicando isso.
     */
    fun marcarEntregue(viagemId: String) {
        viewModelScope.launch {
            val confirmado = RoteiroRepositorio.marcarEntregue(viagemId)
            _estado.value = if (confirmado) {
                _estado.value?.copy(acaoConcluida = Evento(Unit))
            } else {
                _estado.value?.copy(
                    acaoConcluida = Evento(Unit),
                    aviso = Evento(AvisoViagem.ENTREGA_ENFILEIRADA)
                )
            }
        }
    }
}
