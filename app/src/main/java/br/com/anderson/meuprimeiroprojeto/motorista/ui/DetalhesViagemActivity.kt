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
 * Tela de detalhe de uma viagem: dados do pedido e um botão cuja ação muda conforme o contexto
 * (adicionar/remover do roteiro, marcar como entregue). Sem mapa próprio — o trajeto ao vivo e o
 * km calculado vivem na aba Roteiro, já que os pontos de GPS agora pertencem ao roteiro inteiro.
 */
class DetalhesViagemActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VIAGEM_ID = "viagem_id"
    }

    private val viewModel: DetalhesViagemViewModel by viewModels()
    private val formatoData = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))

    private lateinit var viagemId: String
    private lateinit var textPeriodo: TextView
    private lateinit var textKm: TextView
    private lateinit var botaoAcao: Button
    private lateinit var textErro: TextView
    private var estadoAtual: DetalhesViagemUiState = DetalhesViagemUiState()

    /**
     * Infla o layout, resolve o id da viagem recebido por extra (sem ele a tela não faz sentido,
     * então fecha na hora) e começa a observar o [estado][DetalhesViagemViewModel.estado] da
     * ViewModel para redesenhar a tela a cada mudança.
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
     * Recarrega os dados da viagem sempre que a tela volta a ficar visível — por exemplo, depois
     * que o motorista adiciona a viagem ao roteiro e volta pra cá, o botão precisa refletir o
     * novo estado.
     */
    override fun onResume() {
        super.onResume()
        val uid = AutenticacaoRepositorio.usuarioAtual?.uid ?: return
        viewModel.carregar(viagemId, uid)
    }

    /** Preenche todos os campos da tela e o botão de ação a partir do estado atual da ViewModel. */
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

    /** Chama o método da ViewModel correspondente à ação atual do botão (ver [AcaoViagem]). */
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

    private fun mostrarErro(mensagem: String) {
        textErro.text = mensagem
        textErro.visibility = View.VISIBLE
    }
}
