package br.com.anderson.meuprimeiroprojeto.motorista.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import br.com.anderson.meuprimeiroprojeto.R
import br.com.anderson.meuprimeiroprojeto.motorista.formatarM3
import br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel.IniciarTransitoUiState
import br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel.IniciarTransitoViewModel

/**
 * Mostra os dados da viagem que o gerente atribuiu (pedido, endereço, m³, caminhão — nada disso
 * é editável aqui) e deixa o motorista confirmar o início do trânsito.
 */
class IniciarTransitoActivity : AppCompatActivity() {

    companion object {
        /** Chave do extra usado para informar o id da viagem ao abrir esta Activity. */
        const val EXTRA_VIAGEM_ID = "viagem_id"
    }

    private val viewModel: IniciarTransitoViewModel by viewModels()

    private lateinit var viagemId: String
    private lateinit var botaoIniciar: Button
    private lateinit var textErro: TextView

    /** Lê o id da viagem do Intent (fecha a tela se faltar) e começa a observar o estado da ViewModel. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_iniciar_transito)

        val idRecebido = intent.getStringExtra(EXTRA_VIAGEM_ID)
        if (idRecebido.isNullOrBlank()) {
            finish()
            return
        }
        viagemId = idRecebido

        botaoIniciar = findViewById(R.id.botaoIniciar)
        textErro = findViewById(R.id.textErro)
        botaoIniciar.setOnClickListener { tentarIniciar() }

        viewModel.estado.observe(this) { estado -> exibirEstado(estado) }
        viewModel.carregar(viagemId)
    }

    /** Preenche os campos da tela com os dados da viagem, ou mostra erro se falhou ao carregar. */
    private fun exibirEstado(estado: IniciarTransitoUiState) {
        if (estado.erroAoCarregar) {
            mostrarErro(getString(R.string.iniciar_transito_erro_carregar))
            return
        }

        val viagem = estado.viagem ?: return
        findViewById<TextView>(R.id.textDestinatario).text = viagem.destinatarioNome
        findViewById<TextView>(R.id.textEndereco).text = viagem.enderecoResumido
        findViewById<TextView>(R.id.textMetrosCubicos).text = getString(
            R.string.iniciar_transito_m3_formato, formatarM3(viagem.metrosCubicos)
        )
        findViewById<TextView>(R.id.textCaminhao).text = getString(
            R.string.iniciar_transito_caminhao_formato, viagem.caminhaoPlaca
        )
    }

    /** Confirma o início do trânsito e já navega para a tela de viagem em andamento. */
    private fun tentarIniciar() {
        viewModel.iniciarTransito(viagemId)

        Toast.makeText(this, R.string.iniciar_transito_sucesso, Toast.LENGTH_SHORT).show()

        val intent = Intent(this, ViagemEmAndamentoActivity::class.java)
            .putExtra(ViagemEmAndamentoActivity.EXTRA_VIAGEM_ID, viagemId)
        startActivity(intent)
        finish()
    }

    /** Exibe a mensagem de erro na tela. */
    private fun mostrarErro(mensagem: String) {
        textErro.text = mensagem
        textErro.visibility = View.VISIBLE
    }
}
