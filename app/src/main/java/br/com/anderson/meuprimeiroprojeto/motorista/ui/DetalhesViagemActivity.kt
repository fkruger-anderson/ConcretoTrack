package br.com.anderson.meuprimeiroprojeto.motorista.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import br.com.anderson.meuprimeiroprojeto.R
import br.com.anderson.meuprimeiroprojeto.motorista.backend.AutenticacaoRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.formatarKm
import br.com.anderson.meuprimeiroprojeto.motorista.formatarM3
import br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel.AcaoViagem
import br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel.DetalhesViagemUiState
import br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel.DetalhesViagemViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A tela de detalhes de uma viagem: destinatário, endereço, m³, caminhão e um botão de ação.
 *
 * O botão muda conforme a situação (quem decide é o [DetalhesViagemViewModel]):
 * - "Adicionar ao roteiro", se a viagem ainda não está em nenhum roteiro;
 * - "Remover do roteiro", se está num roteiro que ainda não saiu;
 * - "Marcar como entregue", se o roteiro já está em rota;
 * - nenhum botão, se a viagem já foi entregue ou cancelada.
 *
 * Esta tela não tem mapa: o trajeto e os km ficam na aba Roteiro, porque o GPS é do roteiro
 * inteiro, não de uma viagem.
 */
class DetalhesViagemActivity : AppCompatActivity() {

    companion object {
        /**
         * Nome do "extra" que leva o id da viagem. Quem abre esta tela coloca o id no Intent com
         * esse nome, e esta tela o lê de volta no [onCreate].
         */
        const val EXTRA_VIAGEM_ID = "viagem_id"
    }

    private val viewModel: DetalhesViagemViewModel by viewModels()
    // Formato de data e hora, por exemplo "21/09/2026 14:35".
    private val formatoData = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))

    private lateinit var viagemId: String
    private lateinit var textPeriodo: TextView
    private lateinit var textKm: TextView
    private lateinit var botaoAcao: Button
    private lateinit var textErro: TextView
    // Guarda o último estado desenhado, para o clique do botão saber qual ação executar.
    private var estadoAtual: DetalhesViagemUiState = DetalhesViagemUiState()

    /**
     * Chamado pelo Android quando a tela é criada.
     *
     * Lê o id da viagem que veio no Intent. Sem ele a tela não tem o que mostrar, então fecha na
     * hora (`finish()`). Depois encontra os componentes, liga o clique do botão e começa a observar
     * o estado do ViewModel. Os dados em si são carregados no [onResume].
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detalhes_viagem)

        val idRecebido = intent.getStringExtra(EXTRA_VIAGEM_ID)
        if (idRecebido.isNullOrBlank()) {
            finish()
            return
        }
        viagemId = idRecebido

        textPeriodo = findViewById(R.id.textPeriodo)
        textKm = findViewById(R.id.textKm)
        botaoAcao = findViewById(R.id.botaoAcao)
        textErro = findViewById(R.id.textErro)

        botaoAcao.setOnClickListener { executarAcao() }

        viewModel.estado.observe(this) { estado -> exibirEstado(estado) }
    }

    /**
     * Chamado pelo Android toda vez que a tela volta a aparecer na frente (inclusive na primeira
     * vez). Recarrega a viagem, para o botão sempre refletir a situação mais recente.
     */
    override fun onResume() {
        super.onResume()
        val uid = AutenticacaoRepositorio.usuarioAtual?.uid ?: return
        viewModel.carregar(viagemId, uid)
    }

    /**
     * Desenha o estado atual na tela: textos da viagem, período (se já iniciou), km (se o roteiro
     * já foi encerrado) e o botão certo para a situação.
     *
     * No fim, trata os "eventos" de uma vez só: uma mensagem de erro vira um Toast (o aviso que
     * aparece rapidinho na parte de baixo da tela) e uma ação concluída fecha a tela.
     */
    private fun exibirEstado(estado: DetalhesViagemUiState) {
        estadoAtual = estado
        if (estado.erroAoCarregar) {
            mostrarErro(getString(R.string.iniciar_transito_erro_carregar))
            return
        }
        if (!estado.carregado) return

        findViewById<TextView>(R.id.textDestinatario).text = estado.destinatarioNome
        findViewById<TextView>(R.id.textEndereco).text = estado.enderecoResumido
        findViewById<TextView>(R.id.textMetrosCubicos).text = getString(
            R.string.iniciar_transito_m3_formato, formatarM3(estado.metrosCubicos)
        )
        findViewById<TextView>(R.id.textCaminhao).text = getString(
            R.string.iniciar_transito_caminhao_formato, estado.caminhaoPlaca
        )

        if (estado.dataInicio != null) {
            textPeriodo.text = getString(
                R.string.detalhes_viagem_inicio_formato, formatoData.format(Date(estado.dataInicio))
            )
            textPeriodo.visibility = View.VISIBLE
        } else {
            textPeriodo.visibility = View.GONE
        }

        if (estado.kmPercorridos != null) {
            textKm.text = getString(R.string.detalhes_viagem_km_formato, formatarKm(estado.kmPercorridos))
            textKm.visibility = View.VISIBLE
        } else {
            textKm.visibility = View.GONE
        }

        when (estado.acao) {
            AcaoViagem.ADICIONAR_AO_ROTEIRO -> {
                botaoAcao.text = getString(R.string.detalhes_viagem_botao_adicionar)
                botaoAcao.visibility = View.VISIBLE
            }
            AcaoViagem.REMOVER_DO_ROTEIRO -> {
                botaoAcao.text = getString(R.string.detalhes_viagem_botao_remover)
                botaoAcao.visibility = View.VISIBLE
            }
            AcaoViagem.MARCAR_ENTREGUE -> {
                botaoAcao.text = getString(R.string.detalhes_viagem_botao_entregue)
                botaoAcao.visibility = View.VISIBLE
            }
            AcaoViagem.NENHUMA -> botaoAcao.visibility = View.GONE
        }
        botaoAcao.isEnabled = !estado.carregando

        estado.erroAcao?.getConteudoSeNaoTratado()?.let { mensagem ->
            Toast.makeText(this, mensagem, Toast.LENGTH_LONG).show()
        }
        estado.acaoConcluida?.getConteudoSeNaoTratado()?.let {
            finish()
        }
    }

    /**
     * Executado quando o botão é tocado: pede ao ViewModel a ação que o botão está mostrando agora
     * (ver [AcaoViagem]).
     */
    private fun executarAcao() {
        when (estadoAtual.acao) {
            AcaoViagem.ADICIONAR_AO_ROTEIRO -> viewModel.adicionarAoRoteiro(viagemId)
            AcaoViagem.REMOVER_DO_ROTEIRO -> {
                val roteiroId = estadoAtual.roteiroId ?: return
                viewModel.removerDoRoteiro(viagemId, roteiroId)
            }
            AcaoViagem.MARCAR_ENTREGUE -> viewModel.marcarEntregue(viagemId)
            AcaoViagem.NENHUMA -> {}
        }
    }

    /** Mostra uma mensagem de erro no texto vermelho da tela. */
    private fun mostrarErro(mensagem: String) {
        textErro.text = mensagem
        textErro.visibility = View.VISIBLE
    }
}
