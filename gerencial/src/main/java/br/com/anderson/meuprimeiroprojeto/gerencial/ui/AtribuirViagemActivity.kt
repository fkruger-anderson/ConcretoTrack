package br.com.anderson.meuprimeiroprojeto.gerencial.ui

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import br.com.anderson.meuprimeiroprojeto.gerencial.R
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.MotoristaComId
import br.com.anderson.meuprimeiroprojeto.gerencial.dados.Caminhao
import br.com.anderson.meuprimeiroprojeto.gerencial.formatarM3
import br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel.AtribuirErro
import br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel.AtribuirViagemUiState
import br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel.AtribuirViagemViewModel

/** O gerente já chega aqui com o pedido escolhido; só falta motorista, caminhão e quantidade. */
class AtribuirViagemActivity : AppCompatActivity() {

    companion object {
        /** Chave do Intent extra: id do pedido que está recebendo a nova viagem. */
        const val EXTRA_PEDIDO_ID = "pedido_id"
    }

    private val viewModel: AtribuirViagemViewModel by viewModels()

    private var pedidoId: Int = -1
    private lateinit var textPedido: TextView
    private lateinit var spinnerMotorista: Spinner
    private lateinit var spinnerCaminhao: Spinner
    private lateinit var editQuantidadeM3: EditText
    private lateinit var botaoAtribuir: Button
    private lateinit var progresso: ProgressBar
    private lateinit var textErro: TextView
    private lateinit var textSemDados: TextView

    /** Lê o pedido recebido por Intent, liga as Views e manda a ViewModel carregar os dados. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_atribuir_viagem)

        pedidoId = intent.getIntExtra(EXTRA_PEDIDO_ID, -1)
        if (pedidoId <= 0) {
            finish()
            return
        }

        textPedido = findViewById(R.id.textPedido)
        spinnerMotorista = findViewById(R.id.spinnerMotorista)
        spinnerCaminhao = findViewById(R.id.spinnerCaminhao)
        editQuantidadeM3 = findViewById(R.id.editQuantidadeM3)
        botaoAtribuir = findViewById(R.id.botaoAtribuir)
        progresso = findViewById(R.id.progresso)
        textErro = findViewById(R.id.textErro)
        textSemDados = findViewById(R.id.textSemDados)

        botaoAtribuir.setOnClickListener { tentarAtribuir() }

        viewModel.estado.observe(this) { estado -> exibirEstado(estado) }
        viewModel.carregar(pedidoId)
    }

    /** Reflete o [AtribuirViagemUiState] atual nas Views: textos, spinners, erros e sucesso. */
    private fun exibirEstado(estado: AtribuirViagemUiState) {
        if (estado.pedidoNaoEncontrado) {
            finish()
            return
        }

        textPedido.text = getString(
            R.string.atribuir_pedido_formato, estado.pedidoId, estado.destinatarioNome, formatarM3(estado.saldo)
        )

        if (estado.semDadosParaAtribuir) {
            textSemDados.text = getString(R.string.atribuir_sem_dados)
            textSemDados.visibility = View.VISIBLE
            botaoAtribuir.isEnabled = false
        } else {
            textSemDados.visibility = View.GONE
            spinnerMotorista.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, estado.motoristas)
            spinnerCaminhao.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, estado.caminhoes)
            botaoAtribuir.isEnabled = !estado.enviando
        }

        progresso.visibility = if (estado.enviando) View.VISIBLE else View.GONE
        editQuantidadeM3.error = if (estado.erroQuantidade) getString(R.string.atribuir_erro_m3) else null

        val mensagemErro = when (estado.erroAtribuir) {
            AtribuirErro.SALDO_INSUFICIENTE -> getString(R.string.atribuir_erro_saldo)
            AtribuirErro.SEM_CONEXAO -> getString(R.string.atribuir_erro_conexao)
            AtribuirErro.GENERICO -> getString(R.string.atribuir_erro_generico)
            null -> null
        }
        textErro.visibility = if (mensagemErro != null) View.VISIBLE else View.GONE
        if (mensagemErro != null) textErro.text = mensagemErro

        estado.sucesso?.getConteudoSeNaoTratado()?.let {
            Toast.makeText(this, R.string.atribuir_sucesso, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    /** Lê motorista, caminhão e quantidade escolhidos e pede pra ViewModel tentar atribuir. */
    private fun tentarAtribuir() {
        val motorista = spinnerMotorista.selectedItem as? MotoristaComId ?: return
        val caminhao = spinnerCaminhao.selectedItem as? Caminhao ?: return
        viewModel.atribuir(pedidoId, motorista, caminhao, editQuantidadeM3.text.toString())
    }
}
