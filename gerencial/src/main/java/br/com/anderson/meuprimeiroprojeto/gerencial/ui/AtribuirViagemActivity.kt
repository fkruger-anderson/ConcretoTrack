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

/**
 * A tela de atribuir uma viagem. O gerente chega aqui a partir do histórico de um pedido (o pedido
 * já vem escolhido); falta escolher o motorista, o caminhão e a quantidade em m³.
 *
 * Os motoristas e caminhões aparecem em caixas de seleção (`Spinner`, a listinha que abre ao
 * tocar).
 */
class AtribuirViagemActivity : AppCompatActivity() {

    companion object {
        /** Nome do "extra" do Intent que leva o número do pedido que vai receber a viagem. */
        const val EXTRA_PEDIDO_ID = "pedido_id"
    }

    private val viewModel: AtribuirViagemViewModel by viewModels()

    // -1 = "ainda não lido"; o valor verdadeiro vem do Intent no onCreate.
    private var pedidoId: Int = -1
    private lateinit var textPedido: TextView
    private lateinit var spinnerMotorista: Spinner
    private lateinit var spinnerCaminhao: Spinner
    private lateinit var editQuantidadeM3: EditText
    private lateinit var botaoAtribuir: Button
    private lateinit var progresso: ProgressBar
    private lateinit var textErro: TextView
    private lateinit var textSemDados: TextView

    /**
     * Chamado pelo Android quando a tela é criada. Lê o número do pedido que veio no Intent (sem ele
     * a tela fecha), liga os componentes e o botão Atribuir, começa a observar o ViewModel e pede
     * para ele carregar o pedido e as listas.
     */
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

    /**
     * Desenha o estado atual:
     * - pedido não encontrado: fecha a tela;
     * - cabeçalho com número, destinatário e saldo;
     * - sem motorista ou caminhão cadastrado: mostra um aviso e desliga o botão; senão, preenche as
     *   caixas de seleção (um `ArrayAdapter` simples, que usa o `toString()` de cada item);
     * - erros de quantidade ou do servidor, e o Toast de sucesso (que também fecha a tela).
     */
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

    /**
     * Executado ao tocar em Atribuir: pega o motorista e o caminhão escolhidos e o texto da
     * quantidade, e entrega ao ViewModel. O `as?` tenta converter o item escolhido para o tipo
     * certo; se não der (nada escolhido), a função simplesmente para (`?: return`).
     */
    private fun tentarAtribuir() {
        val motorista = spinnerMotorista.selectedItem as? MotoristaComId ?: return
        val caminhao = spinnerCaminhao.selectedItem as? Caminhao ?: return
        viewModel.atribuir(pedidoId, motorista, caminhao, editQuantidadeM3.text.toString())
    }
}
