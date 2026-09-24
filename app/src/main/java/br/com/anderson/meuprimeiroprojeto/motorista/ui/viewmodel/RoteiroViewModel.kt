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
import br.com.anderson.meuprimeiroprojeto.motorista.db.PontoGps
import br.com.anderson.meuprimeiroprojeto.motorista.db.PontoGpsRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.db.StatusRoteiro
import br.com.anderson.meuprimeiroprojeto.motorista.db.StatusViagem
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Uma viagem do roteiro atual, já com o nome do destinatário (que vem do pedido) para mostrar na
 * lista da aba Roteiro.
 */
data class ViagemDoRoteiro(val viagemId: String, val viagem: ViagemRemota, val destinatarioNome: String)

/**
 * Os avisos que a aba Roteiro pode mostrar. A tela escolhe o texto de cada um no `strings.xml`.
 */
enum class AvisoRoteiro {
    /** A entrega foi marcada sem sinal e será enviada sozinha quando a conexão voltar. */
    ENTREGA_ENFILEIRADA,
    /** Não deu para encerrar o roteiro porque não há internet agora. */
    SEM_CONEXAO_ENCERRAR
}

/**
 * O estado da aba Roteiro ([RoteiroFragment][br.com.anderson.meuprimeiroprojeto.motorista.ui.RoteiroFragment]):
 * tudo o que ela precisa para se desenhar.
 */
data class RoteiroUiState(
    /** `true` depois da primeira carga (antes disso a aba não desenha nada). */
    val carregado: Boolean = false,
    /** `false` se o motorista não tem roteiro ativo. */
    val temRoteiro: Boolean = false,
    val roteiroId: String? = null,
    /** Situação do roteiro (nome de `StatusRoteiro`). */
    val status: String = "",
    val viagens: List<ViagemDoRoteiro> = emptyList(),
    /** Pontos de GPS do trajeto até agora (só com o roteiro em rota). */
    val pontos: List<PontoGps> = emptyList(),
    /** Se o botão "Iniciar" pode ser tocado. */
    val podeIniciar: Boolean = false,
    /** Se o botão "Encerrar" pode ser tocado. */
    val podeEncerrar: Boolean = false,
    /** `true` enquanto o encerramento está em andamento. */
    val carregando: Boolean = false,
    /** Aviso para mostrar num Toast, uma vez só. */
    val aviso: Evento<AvisoRoteiro>? = null,
    /**
     * Aviso de "roteiro encerrado com sucesso", uma vez só. Leva o id do roteiro, porque é com ele
     * que a tela desliga o serviço de GPS.
     */
    val roteiroEncerrado: Evento<String>? = null
)

/**
 * O "cérebro" da aba Roteiro: carrega o roteiro ativo do motorista e executa as ações dele
 * (iniciar, marcar entrega, encerrar).
 *
 * É um `ViewModel` comum, sem `Context` (regra da disciplina). Por isso ele NÃO liga nem desliga o
 * serviço de GPS (ligar um serviço exige um `Context`): isso é trabalho da tela, o
 * [RoteiroFragment][br.com.anderson.meuprimeiroprojeto.motorista.ui.RoteiroFragment]. O ViewModel
 * só avisa a tela, pelo [estado], quando o roteiro foi encerrado ([RoteiroUiState.roteiroEncerrado]).
 */
class RoteiroViewModel : ViewModel() {

    companion object {
        // Etiqueta das mensagens desta classe no Logcat do Android Studio.
        private const val TAG = "RoteiroViewModel"

        // Encerrar precisa de conexão na hora (grava o rateio de km) — sem isso o motorista
        // ficaria esperando pra sempre, então cortamos e mostramos erro genérico.
        // O tempo máximo de espera é de 10 segundos.
        private const val TEMPO_LIMITE_MS = 10_000L
    }

    // `_estado` é a versão que o ViewModel pode mudar; `estado` é a versão só de leitura da tela.
    private val _estado = MutableLiveData(RoteiroUiState())
    /** O estado atual da aba; a tela é avisada toda vez que ele muda. */
    val estado: LiveData<RoteiroUiState> = _estado

    /**
     * Carrega a aba inteira para o motorista logado (se ninguém estiver logado, não faz nada):
     * 1. procura o roteiro ativo do motorista (se não houver, publica "sem roteiro" e para);
     * 2. busca as viagens do roteiro e o nome do destinatário de cada uma;
     * 3. se o roteiro está em rota, lê os pontos de GPS do banco local para o mapa;
     * 4. calcula se os botões podem ser usados:
     *    - "Iniciar": roteiro PLANEJANDO com pelo menos uma viagem ATRIBUIDA;
     *    - "Encerrar": roteiro EM_ROTA com todas as viagens resolvidas (entregues ou canceladas).
     *
     * Erros de internet em cada etapa viram "vazio", para a aba mostrar o que for possível, e
     * ficam registrados no Logcat.
     */
    fun carregar() {
        val motoristaUid = AutenticacaoRepositorio.usuarioAtual?.uid ?: return
        viewModelScope.launch {
            val roteiroAtivo = try {
                RoteiroRepositorio.buscarRoteiroAtivo(motoristaUid)
            } catch (erro: Exception) {
                Log.w(TAG, "Falha ao buscar o roteiro ativo do motorista", erro)
                null
            }
            if (roteiroAtivo == null) {
                _estado.value = RoteiroUiState(carregado = true, temRoteiro = false)
                return@launch
            }
            val (roteiroId, roteiro) = roteiroAtivo

            val viagensBrutas = try {
                ViagemRepositorio.buscarPorRoteiro(roteiroId)
            } catch (erro: Exception) {
                Log.w(TAG, "Falha ao buscar as viagens do roteiro $roteiroId", erro)
                emptyList()
            }
            val viagens = viagensBrutas.map { (id, viagem) ->
                val pedido = try {
                    PedidoRepositorio.buscarPorId(viagem.pedidoId)
                } catch (erro: Exception) {
                    Log.w(TAG, "Falha ao buscar o pedido ${viagem.pedidoId}", erro)
                    null
                }
                ViagemDoRoteiro(id, viagem, pedido?.destinatarioNome ?: "")
            }

            val emRota = roteiro.status == StatusRoteiro.EM_ROTA.name
            val pontos = if (emRota) {
                PontoGpsRepositorio.buscarPontos(roteiroId)
            } else {
                emptyList()
            }

            val todasResolvidas = viagens.all { (_, viagem, _) ->
                viagem.status == StatusViagem.CONCLUIDA.name || viagem.status == StatusViagem.CANCELADA.name
            }

            _estado.value = RoteiroUiState(
                carregado = true,
                temRoteiro = true,
                roteiroId = roteiroId,
                status = roteiro.status,
                viagens = viagens,
                pontos = pontos,
                podeIniciar = roteiro.status == StatusRoteiro.PLANEJANDO.name &&
                    viagens.any { (_, viagem, _) -> viagem.status == StatusViagem.ATRIBUIDA.name },
                podeEncerrar = emRota && todasResolvidas
            )
        }
    }

    /**
     * Relê só os pontos de GPS do roteiro, direto do banco do celular (sem internet). A aba chama
     * isto a cada 10 segundos para o trajeto no mapa acompanhar o caminhão, sem repetir as
     * consultas ao Firestore que o [carregar] faz.
     *
     * `copy(pontos = ...)` cria um estado novo igual ao atual, trocando só os pontos.
     */
    fun atualizarPontos() {
        val estado = _estado.value ?: return
        val roteiroId = estado.roteiroId ?: return
        if (estado.status != StatusRoteiro.EM_ROTA.name) return
        viewModelScope.launch {
            val pontos = PontoGpsRepositorio.buscarPontos(roteiroId)
            _estado.value = _estado.value?.copy(pontos = pontos)
        }
    }

    /**
     * Inicia o roteiro no Firestore (sem esperar resposta, funciona sem sinal) e recarrega a aba.
     * Quem liga o serviço de GPS é a tela, logo depois de chamar esta função.
     *
     * Só as viagens que ainda estão ATRIBUIDA vão para EM_ROTA. Se o gerente cancelou uma viagem
     * enquanto o roteiro era montado, os m³ dela já voltaram para o pedido — ela não pode
     * "ressuscitar" aqui.
     */
    fun iniciarRoteiro() {
        val estado = _estado.value ?: return
        val roteiroId = estado.roteiroId ?: return
        val viagemIdsAtribuidas = estado.viagens
            .filter { it.viagem.status == StatusViagem.ATRIBUIDA.name }
            .map { it.viagemId }
        RoteiroRepositorio.iniciarRoteiro(roteiroId, viagemIdsAtribuidas)
        carregar()
    }

    /**
     * Marca uma viagem como entregue (ver [RoteiroRepositorio.marcarEntregue]) e recarrega a aba.
     *
     * Se a confirmação ficou guardada no celular (sem sinal), mostra um aviso; a viagem continua
     * aparecendo como pendente até o reenvio automático conseguir.
     */
    fun marcarEntregue(viagemId: String) {
        viewModelScope.launch {
            val confirmado = RoteiroRepositorio.marcarEntregue(viagemId)
            if (!confirmado) {
                _estado.value = _estado.value?.copy(aviso = Evento(AvisoRoteiro.ENTREGA_ENFILEIRADA))
            }
            carregar()
        }
    }

    /**
     * Encerra o roteiro. Só deve ser chamado quando [RoteiroUiState.podeEncerrar] for `true`.
     *
     * Passo a passo (tudo com no máximo 10 segundos de espera):
     * 1. busca as viagens do roteiro;
     * 2. busca a distância dedicada de cada pedido envolvido (sem repetir pedidos: `distinct`);
     * 3. calcula os km rodados a partir dos pontos de GPS do celular;
     * 4. manda o repositório dividir os km e fechar o roteiro.
     *
     * Deu certo: o motorista fica sem roteiro ativo, então o estado passa direto para "sem roteiro",
     * levando o aviso [RoteiroUiState.roteiroEncerrado] com o id do roteiro — é com ele que a tela
     * desliga o serviço de GPS. O aviso fica guardado no estado até a tela recebê-lo, mesmo que o
     * app esteja em segundo plano nesse momento.
     *
     * Precisa de internet: se não houver, mostra o aviso de "sem conexão" e o motorista pode tentar
     * de novo.
     */
    fun encerrarRoteiro() {
        val roteiroId = _estado.value?.roteiroId ?: return
        _estado.value = _estado.value?.copy(carregando = true)
        viewModelScope.launch {
            try {
                withTimeout(TEMPO_LIMITE_MS) {
                    val viagens = ViagemRepositorio.buscarPorRoteiro(roteiroId)
                    val pedidoIds = viagens.map { (_, viagem) -> viagem.pedidoId }.distinct()
                    val distancias = pedidoIds.associateWith { id ->
                        try {
                            PedidoRepositorio.buscarPorId(id)?.distanciaDedicadaKm ?: 0.0
                        } catch (erro: Exception) {
                            Log.w(TAG, "Falha ao buscar a distância do pedido $id", erro)
                            0.0
                        }
                    }
                    val km = PontoGpsRepositorio.calcularKmPercorridos(roteiroId)
                    RoteiroRepositorio.encerrarRoteiro(roteiroId, km, viagens, distancias)
                }
                _estado.value = RoteiroUiState(
                    carregado = true,
                    temRoteiro = false,
                    roteiroEncerrado = Evento(roteiroId)
                )
            } catch (erro: TimeoutCancellationException) {
                _estado.value = _estado.value?.copy(
                    carregando = false,
                    aviso = Evento(AvisoRoteiro.SEM_CONEXAO_ENCERRAR)
                )
            } catch (erro: Exception) {
                Log.w(TAG, "Falha ao encerrar o roteiro $roteiroId", erro)
                _estado.value = _estado.value?.copy(
                    carregando = false,
                    aviso = Evento(AvisoRoteiro.SEM_CONEXAO_ENCERRAR)
                )
            }
        }
    }
}
